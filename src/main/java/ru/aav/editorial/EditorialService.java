package ru.aav.editorial;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EditorialService {
    private final JdbcTemplate jdbc;
    public record Book(String kind, String id, String title, String author, String section,
                       String description, String source, List<String> lists, String comment, long version, String pdfId, long pdfVersion, boolean customCover, String coverTitle) {}
    private static final String CATALOG = """
        SELECT 'catalog' AS kind, CAST(b.id AS varchar) AS book_id, b.name AS title,
          COALESCE(b.author,'') AS author, COALESCE(s.name,b.category,'Каталог') AS section,
          '' AS original_description, e.description, e.lists, e.comment, e.version,
          p.library_id pdf_id,COALESCE(l.version,0) pdf_version,CASE WHEN c.book_id IS NULL THEN FALSE ELSE TRUE END custom_cover,
          COALESCE(b.cover_title,b.name) cover_title
        FROM (SELECT * FROM books WHERE archived=FALSE) b LEFT JOIN book_editorial e ON e.catalog_id=b.id
        LEFT JOIN store_sections s ON s.catalog_key=b.category
        LEFT JOIN catalog_pdf p ON p.book_id=b.id LEFT JOIN java_library l ON l.id=p.library_id
        LEFT JOIN catalog_covers c ON c.book_id=b.id
        """;
    private static final String LIBRARY = """
        SELECT 'library' AS kind, b.id AS book_id, b.title, b.author, COALESCE(s.name,b.section) section,
          b.description AS original_description, e.description, e.lists, e.comment, e.version,
          b.id pdf_id,b.version pdf_version,b.custom_cover,b.title cover_title
        FROM (SELECT source.* FROM java_library source WHERE NOT EXISTS(SELECT 1 FROM catalog_pdf p WHERE p.library_id=source.id)) b
        LEFT JOIN book_editorial e ON e.library_id=b.id LEFT JOIN store_sections s ON s.library_key=b.section
        """;
    private Book row(java.sql.ResultSet r, int n) throws java.sql.SQLException {
        var summary = BookDescriptions.find(r.getString("title"),r.getString("author"));
        String description = r.getString("description"), source = "";
        if (description == null) {
            String original = r.getString("original_description");
            // New defaults fill empty descriptions only. Explicit editorial edits (including
            // an intentional empty value) already take precedence above. Keep legacy summaries unchanged.
            if (summary.text().isBlank() || (summary.supplemental() && original != null && !original.isBlank())) {
                description = original;
            } else {
                description = summary.text();
                source = summary.source();
            }
        } else if (description.equals(summary.text())) source = summary.source();
        String lists = r.getString("lists");
        return new Book(r.getString("kind"),r.getString("book_id"),r.getString("title"),r.getString("author"),
            r.getString("section"),description == null ? "" : description,source,
            lists == null || lists.isBlank() ? List.of() : List.of(lists.split("\n")),
            Objects.requireNonNullElse(r.getString("comment"),""), r.getObject("version") == null ? -1 : r.getLong("version"),r.getString("pdf_id"),r.getLong("pdf_version"),r.getBoolean("custom_cover"),r.getString("cover_title"));
    }
    @Transactional(readOnly=true)
    public List<Book> list(boolean recommended) {
        String filter = recommended ? " WHERE e.lists IS NOT NULL AND e.lists <> ''" : "";
        return jdbc.query(CATALOG+filter+" UNION ALL "+LIBRARY+filter+" ORDER BY title,kind,book_id",this::row);
    }
    @Transactional(readOnly=true)
    public Book get(String kind,String id) {
        Object key=key(kind,id);
        return jdbc.query((kind.equals("catalog") ? CATALOG : LIBRARY)+" WHERE b.id=?",this::row,key)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Книга не найдена"));
    }
    @Transactional
    public Book save(String kind,String id,EditorialController.Edit edit) {
        Object key=key(kind,id);
        String table=kind.equals("catalog") ? "books" : "java_library";
        String column=kind.equals("catalog") ? "catalog_id" : "library_id";
        // Lock the parent even on the first edit; prevents two concurrent inserts/lost updates.
        if (jdbc.queryForList("SELECT id FROM "+table+" WHERE id=? FOR UPDATE",key).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Книга не найдена");
        Book current=get(kind,id);
        if(current.version()!=edit.version())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Книга уже изменена. Закройте редактор и откройте заново.");
        var lists=new LinkedHashSet<String>();
        for (String value:edit.lists()) {
            String label=value.strip();
            if(label.isBlank() || label.chars().anyMatch(Character::isISOControl))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Название списка не должно содержать переносы строк");
            lists.add(label);
        }
        String names=String.join("\n",lists);
        if(current.version()<0)
            jdbc.update("INSERT INTO book_editorial("+column+",description,lists,comment) VALUES(?,?,?,?)",
                key,edit.description().strip(),names,edit.comment().strip());
        else jdbc.update("UPDATE book_editorial SET description=?,lists=?,comment=?,version=version+1 WHERE "+column+"=?",
            edit.description().strip(),names,edit.comment().strip(),key);
        return get(kind,id);
    }
    private Object key(String kind,String id) {
        try {
            if(kind.equals("catalog")) { long value=Long.parseLong(id); if(value>0)return value; }
            if(kind.equals("library"))return UUID.fromString(id).toString();
        } catch (IllegalArgumentException ignored) {}
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Некорректный идентификатор книги");
    }
}
