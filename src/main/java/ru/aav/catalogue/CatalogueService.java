package ru.aav.catalogue;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.library.*;
import ru.aav.repository.BookRepository;

@Service
@RequiredArgsConstructor
public class CatalogueService {
    private final JdbcTemplate db;
    private final SectionService sections;
    private final BookRepository books;
    private final JavaLibraryService library;
    public record Entry(String kind,String id,String title,String author,String sectionId,String sectionName,
        String description,long version,String pdfId,long pdfVersion,boolean customCover,boolean cover,
        java.math.BigDecimal price,int availableStock,String coverTitle) {}
    public record Catalogue(List<SectionService.Section> sections,List<Entry> books) {}
    private static final String CATALOG="""
        SELECT 'catalog' kind,CAST(b.id AS VARCHAR) book_id,b.name title,COALESCE(b.author,'') author,
        s.id section_id,s.name section_name,COALESCE(e.description,'') description,b.management_revision revision,
        p.library_id pdf_id,COALESCE(l.version,0) pdf_version,CASE WHEN c.book_id IS NULL THEN FALSE ELSE TRUE END custom_cover,
        b.price,b.stock-b.reserved available,COALESCE(b.cover_title,b.name) cover_title
        FROM books b JOIN store_sections s ON s.catalog_key=b.category
        LEFT JOIN catalog_pdf p ON p.book_id=b.id LEFT JOIN java_library l ON l.id=p.library_id
        LEFT JOIN catalog_covers c ON c.book_id=b.id LEFT JOIN book_editorial e ON e.catalog_id=b.id
        WHERE b.archived=FALSE
        """;
    private static final String LIBRARY="""
        SELECT 'library' kind,b.id book_id,b.title,b.author,s.id section_id,s.name section_name,
        b.description,b.version revision,b.id pdf_id,b.version pdf_version,b.custom_cover,
        CAST(NULL AS numeric(10,2)) price,0 available,b.title cover_title
        FROM java_library b JOIN store_sections s ON s.library_key=b.section
        WHERE NOT EXISTS(SELECT 1 FROM catalog_pdf p WHERE p.library_id=b.id)
        """;
    private Entry row(java.sql.ResultSet r,int n)throws java.sql.SQLException {
        String pdf=r.getString("pdf_id");boolean custom=r.getBoolean("custom_cover");
        return new Entry(r.getString("kind"),r.getString("book_id"),r.getString("title"),r.getString("author"),r.getString("section_id"),r.getString("section_name"),
            r.getString("description"),r.getLong("revision"),pdf,r.getLong("pdf_version"),custom,custom||pdf!=null,r.getBigDecimal("price"),r.getInt("available"),r.getString("cover_title"));
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Catalogue list(){return new Catalogue(sections.list(),db.query(CATALOG+" UNION ALL "+LIBRARY+" ORDER BY title,kind,book_id",this::row));}
    @Transactional(readOnly=true) public Entry get(String kind,String id) {
        if(kind.equals("catalog"))return db.query(CATALOG+" AND b.id=?",this::row,catalogId(id)).stream().findFirst().orElseThrow(this::missing);
        if(kind.equals("library"))return db.query(LIBRARY+" AND b.id=?",this::row,libraryId(id)).stream().findFirst().orElseThrow(this::missing);
        throw missing();
    }
    @Transactional public void create(String title,String author,String sectionId,String description,LibraryFiles.Prepared files,boolean uploadedCover) {
        var section=sections.lock(sectionId);
        var book=new ru.aav.model.Book();book.setName(title.strip());book.setAuthor(author.strip());book.setCategory(section.catalogKey());
        books.saveAndFlush(book);
        db.update("INSERT INTO book_editorial(catalog_id,description) VALUES(?,?)",book.getId(),description.strip());
        saveCatalogFile(book.getId(),null,title,author,description,section,files,uploadedCover,false);
    }
    @Transactional public void update(String kind,String id,long version,long pdfVersion,String title,String author,String sectionId,String description,
        LibraryFiles.Prepared files,boolean uploadedCover,boolean removePdf) {
        var section=sections.lock(sectionId);
        if(kind.equals("library")) {
            String key=libraryId(id);var current=get(kind,key);if(current.version()!=version)throw conflict();
            if(removePdf)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Для удаления PDF удалите карточку книги");
            byte[] cover=files.cover();
            if(!uploadedCover && current.customCover() && files.pdf()!=null)cover=library.cover(key).bytes();
            var effective=new LibraryFiles.Prepared(files.pdf(),cover,files.pages(),files.size());
            library.update(key,version,title,author,description,effective);
            db.update("UPDATE java_library SET section=?,custom_cover=? WHERE id=?",section.libraryKey(),uploadedCover||current.customCover(),key);
            return;
        }
        if(!kind.equals("catalog"))throw missing();
        long key=catalogId(id);var book=books.findLockedById(key).orElseThrow(this::missing);
        if(book.isArchived())throw missing();if(book.getManagementRevision()!=version)throw conflict();
        var current=get("catalog",id);
        if(current.pdfId()!=null && current.pdfVersion()!=pdfVersion)throw conflict();
        book.setName(title.strip());book.setAuthor(author.strip());book.setCategory(section.catalogKey());book.setManagementRevision(version+1);
        books.flush();
        saveCatalogFile(key,current,title,author,description,section,files,uploadedCover,removePdf);
    }
    private void saveCatalogFile(long key,Entry current,String title,String author,String description,SectionService.Section section,
        LibraryFiles.Prepared files,boolean uploadedCover,boolean removePdf) {
        if(removePdf && files.pdf()!=null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Выберите замену или удаление PDF");
        if(uploadedCover) {
            db.update("DELETE FROM catalog_covers WHERE book_id=?",key);
            db.update("INSERT INTO catalog_covers(book_id,data) VALUES(?,?)",key,files.cover());
        }
        if(current!=null && current.pdfId()!=null && removePdf) {
            if(db.queryForObject("SELECT COUNT(*) FROM catalog_covers WHERE book_id=?",Integer.class,key)==0)
                db.update("INSERT INTO catalog_covers(book_id,data) VALUES(?,?)",key,library.cover(current.pdfId()).bytes());
            library.delete(current.pdfId(),current.pdfVersion());return;
        }
        if(files.pdf()==null && (current==null || current.pdfId()==null))return;
        String pdfId=current==null?null:current.pdfId();
        byte[] cover=files.cover();
        var custom=db.query("SELECT data FROM catalog_covers WHERE book_id=?",(r,n)->r.getBytes(1),key);
        if(!custom.isEmpty())cover=custom.getFirst();
        var effective=new LibraryFiles.Prepared(files.pdf(),cover,files.pages(),files.size());
        if(pdfId==null) {
            var created=library.create(title,author,description,section.libraryKey(),effective);
            db.update("INSERT INTO catalog_pdf(book_id,library_id) VALUES(?,?)",key,created.id());
        } else {
            library.update(pdfId,current.pdfVersion(),title,author,description,effective);
            db.update("UPDATE java_library SET section=? WHERE id=?",section.libraryKey(),pdfId);
        }
    }
    @Transactional public void delete(String kind,String id,long version,long pdfVersion) {
        if(kind.equals("library")){get(kind,id);library.delete(libraryId(id),version);return;}
        if(!kind.equals("catalog"))throw missing();
        long key=catalogId(id);var book=books.findLockedById(key).orElseThrow(this::missing);
        if(book.isArchived())throw missing();if(book.getManagementRevision()!=version)throw conflict();
        var current=get("catalog",id);if(current.pdfId()!=null){if(current.pdfVersion()!=pdfVersion)throw conflict();library.delete(current.pdfId(),pdfVersion);}
        // Keep historical foreign keys while freeing the active catalogue title for reuse.
        book.setRetiredTitle(book.getName());book.setName("[archived:"+key+"] "+UUID.randomUUID());
        book.setArchived(true);book.setManagementRevision(version+1);
        db.update("DELETE FROM user_favorites WHERE book_id=?",key);db.update("DELETE FROM cart_items WHERE book_id=?",key);
        db.update("DELETE FROM book_editorial WHERE catalog_id=?",key);db.update("DELETE FROM catalog_covers WHERE book_id=?",key);
    }
    @Transactional(readOnly=true) public JavaLibraryService.File cover(long id) {
        var current=get("catalog",String.valueOf(id));
        var covers=db.query("SELECT data FROM catalog_covers WHERE book_id=?",(r,n)->r.getBytes(1),id);
        if(!covers.isEmpty())return new JavaLibraryService.File(current.title(),covers.getFirst());
        if(current.pdfId()!=null)return library.cover(current.pdfId());throw missing();
    }
    private long catalogId(String id){try{long n=Long.parseLong(id);if(n>0)return n;}catch(NumberFormatException ignored){}throw missing();}
    private String libraryId(String id){try{return UUID.fromString(id).toString();}catch(IllegalArgumentException e){throw missing();}}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Книга не найдена");}
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Книга уже изменена. Откройте редактор заново.");}
}
