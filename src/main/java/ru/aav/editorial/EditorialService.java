package ru.aav.editorial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.catalogue.CatalogueOrderService;
import ru.aav.text.TextStyles;
import ru.aav.text.TextStyles.Mark;

@Service
@RequiredArgsConstructor
public class EditorialService {
    private static final String HOME_LIST = "На главной";
    private final JdbcTemplate jdbc;
    private final EditorialBookMapper mapper;
    private final TextStyles styles;
    private final CatalogueOrderService catalogueOrder;

    public record Book(
        String kind,
        String id,
        String title,
        String author,
        String section,
        String description,
        String source,
        List<String> lists,
        String comment,
        long version,
        String pdfId,
        long pdfVersion,
        boolean customCover,
        String coverTitle,
        List<Mark> descriptionStyle,
        List<Mark> commentStyle
    ) {}
    private static final String CATALOG = """
        SELECT 'catalog' AS kind, CAST(b.id AS varchar) AS book_id, b.name AS title,
          COALESCE(b.author,'') AS author, COALESCE(s.name,b.category,'Каталог') AS section,
          '' AS original_description, e.description, e.lists, e.comment, e.version, e.description_style, e.comment_style,
          p.library_id pdf_id,COALESCE(l.version,0) pdf_version,CASE WHEN c.book_id IS NULL THEN FALSE ELSE TRUE END custom_cover,
          COALESCE(b.cover_title,b.name) cover_title
        FROM (SELECT * FROM books WHERE archived=FALSE) b LEFT JOIN book_editorial e ON e.catalog_id=b.id
        LEFT JOIN store_sections s ON s.catalog_key=b.category
        LEFT JOIN catalog_pdf p ON p.book_id=b.id LEFT JOIN java_library l ON l.id=p.library_id
        LEFT JOIN catalog_covers c ON c.book_id=b.id
        """;
    private static final String LIBRARY = """
        SELECT 'library' AS kind, b.id AS book_id, b.title, b.author, COALESCE(s.name,b.section) section,
          b.description AS original_description, e.description, e.lists, e.comment, e.version, e.description_style, e.comment_style,
          b.id pdf_id,b.version pdf_version,b.custom_cover,b.title cover_title
        FROM (SELECT source.* FROM java_library source WHERE NOT EXISTS(SELECT 1 FROM catalog_pdf p WHERE p.library_id=source.id)) b
        LEFT JOIN book_editorial e ON e.library_id=b.id LEFT JOIN store_sections s ON s.library_key=b.section
        """;
    @Transactional(readOnly=true)
    public List<Book> list(boolean recommended) {
        String filter = recommended ? " WHERE e.lists IS NOT NULL AND e.lists <> ''" : "";
        return jdbc.query(CATALOG+filter+" UNION ALL "+LIBRARY+filter+" ORDER BY title,kind,book_id",mapper);
    }
    @Transactional(readOnly=true)
    public Book get(String kind, String id) {
        Object key = key(kind, id);
        return jdbc.query((kind.equals("catalog") ? CATALOG : LIBRARY)+" WHERE b.id=?",mapper,key)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Книга не найдена"));
    }
    @Transactional
    public Book save(String kind, String id, EditorialController.Edit edit) {
        Object key = key(kind, id);
        // All editorial saves use the catalogue lock before parent locks. This also
        // serializes replacing a homepage choice across catalog and PDF books.
        catalogueOrder.lock();
        String table = kind.equals("catalog") ? "books" : "java_library";
        String column = kind.equals("catalog") ? "catalog_id" : "library_id";
        // Lock the parent even on the first edit; prevents two concurrent inserts/lost updates.
        if (jdbc.queryForList("SELECT id FROM "+table+" WHERE id=? FOR UPDATE", key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Книга не найдена");
        }
        Book current = get(kind, id);
        if (current.version() != edit.version()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Книга уже изменена. Закройте редактор и откройте заново.");
        }
        var prepared = prepare(edit, current);
        persist(column, key, current, prepared);
        if (prepared.onHome()) removeOtherHomeChoices(column, key);
        return get(kind, id);
    }

    private record PreparedEdit(String description, String names, String comment,
                                String descriptionStyle, String commentStyle, boolean onHome) {}

    private PreparedEdit prepare(EditorialController.Edit edit, Book current) {
        var lists = normalizedLists(edit.lists());
        String names = String.join("\n", lists);
        String descriptionStyle = styles.write(styles.resolve(edit.description(), edit.descriptionStyle(),
            current.description(), current.descriptionStyle()));
        String commentStyle = styles.write(styles.resolve(edit.comment(), edit.commentStyle(),
            current.comment(), current.commentStyle()));
        return new PreparedEdit(edit.description().strip(), names, edit.comment().strip(),
            descriptionStyle, commentStyle, lists.contains(HOME_LIST));
    }

    private List<String> normalizedLists(List<String> requested) {
        var lists = new LinkedHashSet<String>();
        for (String value : requested) {
            String label = value.strip();
            if (label.isBlank() || label.chars().anyMatch(Character::isISOControl)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Название списка не должно содержать переносы строк");
            }
            lists.add(label);
        }
        return List.copyOf(lists);
    }

    private void persist(String column, Object key, Book current, PreparedEdit edit) {
        if (current.version() < 0) {
            jdbc.update("INSERT INTO book_editorial("+column+",description,lists,comment,description_style,comment_style) VALUES(?,?,?,?,?,?)",
                key, edit.description(), edit.names(), edit.comment(), edit.descriptionStyle(), edit.commentStyle());
        } else {
            jdbc.update("UPDATE book_editorial SET description=?,lists=?,comment=?,description_style=?,comment_style=?,version=version+1 WHERE "+column+"=?",
                edit.description(), edit.names(), edit.comment(), edit.descriptionStyle(), edit.commentStyle(), key);
        }
    }

    private record Recommendation(long id, String lists) {}

    private void removeOtherHomeChoices(String column, Object key) {
        var others = jdbc.query("SELECT id,lists FROM book_editorial WHERE " + column + " IS NULL OR " + column + "<>?",
            (r,n) -> new Recommendation(r.getLong("id"), r.getString("lists")), key);
        for (var recommendation : others) {
            var labels = new ArrayList<>(Arrays.asList(recommendation.lists().split("\n")));
            if (labels.removeIf(HOME_LIST::equals)) {
                jdbc.update("UPDATE book_editorial SET lists=?,version=version+1 WHERE id=?",
                    String.join("\n", labels), recommendation.id());
            }
        }
    }
    private Object key(String kind, String id) {
        try {
            if (kind.equals("catalog")) {
                long value = Long.parseLong(id);
                if (value > 0) return value;
            }
            if (kind.equals("library")) return UUID.fromString(id).toString();
        } catch (IllegalArgumentException ignored) {}
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный идентификатор книги");
    }
}
