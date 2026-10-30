package ru.aav.about;

import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AboutService {
    private final JdbcTemplate db;
    private static final Map<String,String> DEFAULT_IMAGES = Map.of(
        "portrait", "/static/covers/author-andrey.jpeg",
        "development", "/static/covers/scene/about-development.jpeg",
        "library", "/static/covers/scene/catalog-library.jpg");
    public record Change(@NotNull @Size(max=160) String title, @NotNull @Size(max=1000) String lead,
        @NotNull @Size(max=20000) String body, @NotNull @Size(max=160) String signature,
        @NotNull @Size(max=120) String linkLabel, @NotNull @Size(max=255) String photoAlt,
        @Min(0) long version, boolean removePortrait, boolean removeDevelopment, boolean removeLibrary) {}
    public record Content(String title,String lead,String body,String signature,String linkLabel,String photoAlt,
        long version,boolean portrait,boolean development,boolean library) {}
    public record Picture(byte[] bytes,String contentType,long version) {}

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Content read() {
        var flags=db.query("SELECT slot,enabled FROM site_about_images",(r,n)->Map.entry(r.getString(1),r.getBoolean(2)));
        var enabled=new java.util.HashMap<String,Boolean>();flags.forEach(e->enabled.put(e.getKey(),e.getValue()));
        return db.queryForObject("SELECT title,lead,body,signature,link_label,photo_alt,version FROM site_about WHERE id=1",
            (r,n)->new Content(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getLong(7),
                enabled.get("portrait"),enabled.get("development"),enabled.get("library")));
    }
    @Transactional
    public Content save(Change c, Map<String,byte[]> uploads) {
        var removals=Map.of("portrait",c.removePortrait(),"development",c.removeDevelopment(),"library",c.removeLibrary());
        for(var entry:uploads.entrySet()) {
            if(!DEFAULT_IMAGES.containsKey(entry.getKey()) || removals.get(entry.getKey()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Выберите замену или удаление фотографии");
        }
        int count=db.update("UPDATE site_about SET title=?,lead=?,body=?,signature=?,link_label=?,photo_alt=?,version=version+1 WHERE id=1 AND version=?",
            c.title().strip(),c.lead().strip(),c.body().strip(),c.signature().strip(),c.linkLabel().strip(),c.photoAlt().strip(),c.version());
        if(count!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Раздел изменён в другом окне. Закройте редактор и откройте его заново.");
        for(var slot:DEFAULT_IMAGES.keySet()) {
            if(removals.get(slot)) db.update("UPDATE site_about_images SET enabled=FALSE,data=NULL WHERE slot=?",slot);
            else if(uploads.containsKey(slot)) db.update("UPDATE site_about_images SET enabled=TRUE,data=? WHERE slot=?",uploads.get(slot),slot);
        }
        return read();
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Picture image(String slot) throws IOException {
        if(!DEFAULT_IMAGES.containsKey(slot)) throw missing();
        var rows=db.query("SELECT i.enabled,i.data,a.version FROM site_about_images i CROSS JOIN site_about a WHERE i.slot=? AND a.id=1",
            (r,n)->new Object[]{r.getBoolean(1),r.getBytes(2),r.getLong(3)},slot);
        if(rows.isEmpty() || !((Boolean)rows.getFirst()[0])) throw missing();
        var row=rows.getFirst();byte[] bytes=(byte[])row[1];String type="image/png";
        if(bytes==null) {
            try(var in=getClass().getResourceAsStream(DEFAULT_IMAGES.get(slot))) {
                if(in==null) throw missing();bytes=in.readAllBytes();type="image/jpeg";
            }
        }
        return new Picture(bytes,type,(Long)row[2]);
    }
    private ResponseStatusException missing() {return new ResponseStatusException(HttpStatus.NOT_FOUND,"Фотография не найдена");}
}
