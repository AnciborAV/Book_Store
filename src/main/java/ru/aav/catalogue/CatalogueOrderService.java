package ru.aav.catalogue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CatalogueOrderService {
    private final JdbcTemplate db;
    public record BookKey(@Pattern(regexp="catalog|library") @NotBlank String kind,
                          @NotBlank @Size(max=36) String id) {}

    // Use the same lock as section creation/renaming, before taking book or section locks.
    public void lock() { db.queryForList("SELECT id FROM catalogue_state WHERE id=1 FOR UPDATE"); }
    public long version() { return db.queryForObject("SELECT order_version FROM catalogue_state WHERE id=1",Long.class); }
    public List<CatalogueService.Entry> sort(List<CatalogueService.Entry> entries) {
        Map<BookKey,Long> positions=new HashMap<>();
        db.query("SELECT kind,book_key,sort_order FROM catalogue_book_order",r->{
            positions.put(new BookKey(r.getString("kind"),r.getString("book_key")),r.getLong("sort_order"));
        });
        // Stable sorting retains the existing title order for newly added, unplaced books.
        return entries.stream().sorted(Comparator.comparingLong(e->positions.getOrDefault(new BookKey(e.kind(),e.id()),Long.MAX_VALUE))).toList();
    }
    @Transactional
    public void save(long expectedVersion,List<BookKey> requested) {
        lock();
        if(version()!=expectedVersion)throw conflict();
        var unique=new HashSet<>(requested);
        if(unique.size()!=requested.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Одна книга указана несколько раз");
        var current=db.query("""
            SELECT 'catalog' kind,CAST(b.id AS VARCHAR) book_key FROM books b
            JOIN store_sections s ON s.catalog_key=b.category WHERE b.archived=FALSE
            UNION ALL
            SELECT 'library' kind,b.id book_key FROM java_library b JOIN store_sections s ON s.library_key=b.section
            WHERE NOT EXISTS(SELECT 1 FROM catalog_pdf p WHERE p.library_id=b.id)
            """,(r,n)->new BookKey(r.getString("kind"),r.getString("book_key")));
        if(!unique.equals(new HashSet<>(current)))throw conflict();
        db.update("DELETE FROM catalogue_book_order");
        List<Object[]> batch=new ArrayList<>();
        for(int i=0;i<requested.size();i++){var key=requested.get(i);batch.add(new Object[]{key.kind(),key.id(),i});}
        if(!batch.isEmpty())db.batchUpdate("INSERT INTO catalogue_book_order(kind,book_key,sort_order) VALUES(?,?,?)",batch);
        db.update("UPDATE catalogue_state SET order_version=order_version+1 WHERE id=1");
    }
    public void remove(String kind,String id) { db.update("DELETE FROM catalogue_book_order WHERE kind=? AND book_key=?",kind,id); }
    public void promote(String libraryId,long catalogId) {
        db.update("UPDATE catalogue_book_order SET kind='catalog',book_key=? WHERE kind='library' AND book_key=?",String.valueOf(catalogId),libraryId);
    }
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Каталог или порядок книг изменился. Обновите полку и повторите расстановку.");}
}
