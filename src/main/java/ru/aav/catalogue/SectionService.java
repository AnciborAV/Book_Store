package ru.aav.catalogue;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SectionService {
    private final JdbcTemplate db;
    public record Section(String id,String name,String catalogKey,String libraryKey,int position,long version) {}
    private Section row(java.sql.ResultSet r,int n)throws java.sql.SQLException {
        return new Section(r.getString("id"),r.getString("name"),r.getString("catalog_key"),r.getString("library_key"),r.getInt("sort_order"),r.getLong("version"));
    }
    @Transactional(readOnly=true) public List<Section> list(){return db.query("SELECT * FROM store_sections ORDER BY sort_order,name,id",this::row);}
    @Transactional(readOnly=true) public Section get(String id){return db.query("SELECT * FROM store_sections WHERE id=?",this::row,id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Раздел не найден"));}
    public Section lock(String id) {
        if(db.queryForList("SELECT id FROM store_sections WHERE id=? FOR UPDATE",id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Раздел не найден");
        return get(id);
    }
    private void lockNames(){db.queryForList("SELECT id FROM catalogue_state WHERE id=1 FOR UPDATE");}
    private String name(String value,String except) {
        String name=value.strip();
        if(name.isBlank() || name.chars().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Введите название раздела без переносов строк");
        if(db.queryForObject("SELECT COUNT(*) FROM store_sections WHERE LOWER(name)=LOWER(?) AND id<>?",Integer.class,name,except)>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Раздел с таким названием уже существует");
        return name;
    }
    @Transactional public String ensureCatalog(String category) {
        lockNames();String key=category==null||category.isBlank()?"Каталог":category.strip();
        var existing=db.query("SELECT * FROM store_sections WHERE catalog_key=? OR LOWER(name)=LOWER(?)",this::row,key,key);
        if(!existing.isEmpty())return existing.getFirst().catalogKey();
        String id=UUID.randomUUID().toString();String label=key.length()>80?key.substring(0,80):key;
        label=name(label,"");
        int position=db.queryForObject("SELECT COALESCE(MAX(sort_order),0)+10 FROM store_sections",Integer.class);
        db.update("INSERT INTO store_sections(id,name,catalog_key,library_key,sort_order) VALUES(?,?,?,?,?)",id,label,key,id.replace("-",""),position);
        return key;
    }
    @Transactional public Section create(String value) {
        lockNames();String id=UUID.randomUUID().toString();String label=name(value,"");
        int position=db.queryForObject("SELECT COALESCE(MAX(sort_order),0)+10 FROM store_sections",Integer.class);
        db.update("INSERT INTO store_sections(id,name,catalog_key,library_key,sort_order) VALUES(?,?,?,?,?)",id,label,"section-"+id,id.replace("-",""),position);
        return get(id);
    }
    @Transactional public Section update(String id,String value,int position,long version) {
        lockNames();var section=lock(id);if(section.version()!=version)throw conflict();
        db.update("UPDATE store_sections SET name=?,sort_order=?,version=version+1 WHERE id=?",name(value,id),position,id);return get(id);
    }
    @Transactional public void delete(String id,long version) {
        lockNames();var section=lock(id);if(section.version()!=version)throw conflict();
        int count=db.queryForObject("SELECT COUNT(*) FROM books WHERE category=? AND archived=FALSE",Integer.class,section.catalogKey())+
            db.queryForObject("SELECT COUNT(*) FROM java_library b WHERE section=? AND NOT EXISTS(SELECT 1 FROM catalog_pdf p WHERE p.library_id=b.id)",Integer.class,section.libraryKey());
        if(count>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Сначала перенесите или удалите книги из раздела");
        db.update("DELETE FROM store_sections WHERE id=?",id);
    }
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Раздел уже изменён. Обновите список.");}
}
