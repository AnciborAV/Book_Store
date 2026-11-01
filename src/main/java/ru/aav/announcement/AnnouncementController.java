package ru.aav.announcement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.text.TextStyles;
import ru.aav.text.TextStyles.Mark;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {
    private final JdbcTemplate jdbc;
    private final TextStyles styles;

    public record Notice(
        String body,
        boolean published,
        int version,
        List<Mark> bodyStyle
    ) {}

    public record Change(
        @NotBlank @Size(max = 1500) String body,
        boolean published,
        @Min(0) int version,
        @Size(max = 5000) List<@Valid Mark> bodyStyle
    ) {}

    @GetMapping("/api/announcement")
    public Notice published() {
        Notice n = read();
        return n.published() ? n : new Notice("", false, n.version(), List.of());
    }

    @GetMapping("/api/admin/announcement")
    public Notice draft() {
        return read();
    }

    @PutMapping("/api/admin/announcement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@Valid @RequestBody Change input) {
        if (input.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите текст объявления");
        }
        Notice current = read();
        String bodyStyle = styles.write(
            styles.resolve(input.body(), input.bodyStyle(), current.body(), current.bodyStyle())
        );
        int count = jdbc.update(
            "UPDATE site_announcement SET body=?, published=?, body_style=?, version=version+1 WHERE id=1 AND version=?",
            input.body().strip(), input.published(), bodyStyle, input.version()
        );
        if (count != 1) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Объявление изменено в другом окне. Откройте редактор заново."
            );
        }
    }

    private Notice read() {
        return jdbc.queryForObject(
            "SELECT body, published, version, body_style FROM site_announcement WHERE id=1",
            (rs, n) -> new Notice(
                rs.getString(1), rs.getBoolean(2), rs.getInt(3), styles.read(rs.getString(4))
            )
        );
    }
}
