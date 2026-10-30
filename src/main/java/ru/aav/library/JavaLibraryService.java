package ru.aav.library;

import java.util.*;
import java.time.Instant;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Isolation;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class JavaLibraryService {
    private final JdbcTemplate jdbc;
    public record Book(String id, String title, String author, String description, long fileSize,
                       int pageCount, long version, Instant updatedAt, String section) {}
    public record Page(List<Book> items, long total, int page, int pageSize) {}
    public record File(String title, byte[] bytes) {}
    private static final RowMapper<Book> ROW = (r,n) -> new Book(r.getString("id"),r.getString("title"),
        r.getString("author"),r.getString("description"),r.getLong("file_size"),r.getInt("page_count"),
        r.getLong("version"),r.getTimestamp("updated_at").toInstant(),r.getString("section"));

    @Transactional(readOnly=true)
    public Page list(int page, String query, String section) {
        String q = "%" + query.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_") + "%";
        String where = " WHERE NOT EXISTS(SELECT 1 FROM catalog_pdf p WHERE p.library_id=java_library.id) AND section=? AND (LOWER(title) LIKE ? ESCAPE '!' OR LOWER(author) LIKE ? ESCAPE '!')";
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM java_library"+where,Long.class,section,q,q);
        return new Page(jdbc.query("SELECT * FROM java_library"+where+" ORDER BY created_at DESC,id LIMIT 12 OFFSET ?",ROW,section,q,q,(long)page*12),count,page,12);
    }
    @Transactional(readOnly=true)
    public List<Book> metadata(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbc.query("SELECT * FROM java_library WHERE id IN (" + placeholders + ")", ROW,
            ids.stream().map(UUID::toString).toArray());
    }
    @Transactional
    public Book create(String title,String author,String description,String section,LibraryFiles.Prepared files) {
        if (jdbc.queryForList("SELECT id FROM store_sections WHERE library_key=? FOR UPDATE",section).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Раздел не найден");
        if (files.pdf()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Выберите PDF книги");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO java_library(id,title,author,description,file_size,page_count,section) VALUES(?,?,?,?,?,?,?)",
            id,title.strip(),author.strip(),description.strip(),files.size(),files.pages(),section);
        jdbc.update("INSERT INTO java_library_files(book_id,cover) VALUES(?,?)",id,files.cover());
        savePdf(id,files);
        return get(id);
    }
    @Transactional
    public Book update(String id,long version,String title,String author,String description,LibraryFiles.Prepared files) {
        lock(id,version);
        jdbc.update("UPDATE java_library SET title=?,author=?,description=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            title.strip(),author.strip(),description.strip(),id);
        if(files.pdf()!=null) {
            jdbc.update("DELETE FROM java_library_pdf_chunks WHERE book_id=?",id);
            savePdf(id,files);
            jdbc.update("UPDATE java_library SET file_size=?,page_count=? WHERE id=?",files.size(),files.pages(),id);
        }
        if(files.cover()!=null) jdbc.update("UPDATE java_library_files SET cover=? WHERE book_id=?",files.cover(),id);
        return get(id);
    }
    @Transactional
    public void delete(String id,long version) { lock(id,version); jdbc.update("DELETE FROM java_library WHERE id=?",id); }
    private void lock(String id,long version) {
        var rows=jdbc.queryForList("SELECT version FROM java_library WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw missing();
        if(((Number)rows.getFirst().get("version")).longValue()!=version)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Книга уже изменена. Обновите список и повторите действие.");
    }
    private Book get(String id) { return jdbc.query("SELECT * FROM java_library WHERE id=?",ROW,id).stream().findFirst().orElseThrow(this::missing); }
    @Transactional(readOnly=true)
    public File cover(String id) {
        return jdbc.query("SELECT b.title,f.cover FROM java_library b JOIN java_library_files f ON f.book_id=b.id WHERE b.id=?",
            (r,n)->new File(r.getString(1),r.getBytes(2)),id).stream().findFirst().orElseThrow(this::missing);
    }
    private void savePdf(String id, LibraryFiles.Prepared files) {
        try (var in=Files.newInputStream(files.pdf())) {
            int part=0; byte[] block;
            while ((block=in.readNBytes(1024*1024)).length>0)
                jdbc.update("INSERT INTO java_library_pdf_chunks(book_id,part,data) VALUES(?,?,?)",id,part++,block);
        } catch(IOException ex) { throw new UncheckedIOException("Не удалось сохранить PDF",ex); }
    }
    // Repeatable snapshot prevents mixing old/new chunks during simultaneous replacement/deletion.
    // One chunk per SELECT bounds JDBC memory, unlike collecting every BLOB in a list.
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public void download(String id,HttpServletResponse response) throws IOException {
        Book book=get(id);
        String name=book.title().replaceAll("[\\p{Cntrl}/\\\\]","_")+".pdf";
        response.setContentType("application/pdf"); response.setContentLengthLong(book.fileSize());
        response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString());
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
        var out=response.getOutputStream();
        long remaining=book.fileSize();
        for(int part=0;remaining>0;part++) {
            byte[] block=jdbc.queryForObject("SELECT data FROM java_library_pdf_chunks WHERE book_id=? AND part=?",byte[].class,id,part);
            if(block==null || block.length==0 || block.length>remaining) throw new IOException("Incomplete PDF storage");
            out.write(block);remaining-=block.length;
        }
        out.flush();
    }
    // Version matching protects separate range requests from mixing editions.
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public void read(String id,long version,String range,HttpServletResponse response) throws IOException {
        Book book=get(id);
        if (book.version()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"Книга обновлена. Откройте её заново.");
        long size=book.fileSize(), start=0, end=size-1;
        response.setHeader("Accept-Ranges","bytes");
        response.setHeader("Cache-Control","no-store");
        if (range!=null) {
            try {
                var ranges=org.springframework.http.HttpRange.parseRanges(range);
                if(ranges.size()!=1) throw new IllegalArgumentException();
                start=ranges.getFirst().getRangeStart(size); end=ranges.getFirst().getRangeEnd(size);
                if(start<0 || start>=size || end<start) throw new IllegalArgumentException();
            } catch(IllegalArgumentException ex) {
                response.setStatus(416);response.setHeader("Content-Range","bytes */"+size);return;
            }
            response.setStatus(206);response.setHeader("Content-Range","bytes "+start+"-"+end+"/"+size);
        }
        response.setContentType("application/pdf");response.setContentLengthLong(end-start+1);
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Content-Disposition",ContentDisposition.inline().filename("book.pdf").build().toString());
        final int chunkSize=1024*1024;
        var out=response.getOutputStream();
        for(long offset=start;offset<=end;) {
            int part=(int)(offset/chunkSize), local=(int)(offset%chunkSize);
            byte[] block=jdbc.queryForObject("SELECT data FROM java_library_pdf_chunks WHERE book_id=? AND part=?",byte[].class,id,part);
            if(block==null || local>=block.length) throw new IOException("Incomplete PDF storage");
            int count=(int)Math.min(block.length-local,end-offset+1);
            out.write(block,local,count); offset+=count;
        }
        out.flush();
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Книга не найдена"); }
}
