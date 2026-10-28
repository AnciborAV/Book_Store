package ru.aav.announcement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {
    private final JdbcTemplate jdbc;
    public record Notice(String body, boolean published, int version) {}
    public record Change(@NotBlank @Size(max=1500) String body, boolean published, @Min(0) int version) {}
    private Notice read() {
        return jdbc.queryForObject("SELECT body, published, version FROM site_announcement WHERE id=1",
            (rs,n) -> new Notice(rs.getString(1),rs.getBoolean(2),rs.getInt(3)));
    }
    @GetMapping("/api/announcement")
    public Notice published() {
        Notice n=read();
        return n.published() ? n : new Notice("",false,n.version());
    }
    @GetMapping("/api/admin/announcement")
    public Notice draft() { return read(); }
    @PutMapping("/api/admin/announcement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@Valid @RequestBody Change input) {
        if(input.body().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Введите текст объявления");
        int count=jdbc.update("UPDATE site_announcement SET body=?, published=?, version=version+1 WHERE id=1 AND version=?",
            input.body().strip(),input.published(),input.version());
        if(count!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Объявление изменено в другом окне. Откройте редактор заново.");
    }
}
