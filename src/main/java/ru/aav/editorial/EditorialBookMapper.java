package ru.aav.editorial;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import ru.aav.text.TextStyles;

/** Builds the existing public book DTO from either catalogue or library rows. */
@Component
@RequiredArgsConstructor
public class EditorialBookMapper implements RowMapper<EditorialService.Book> {
    private final TextStyles styles;

    @Override
    public EditorialService.Book mapRow(ResultSet row, int rowNumber) throws SQLException {
        var description = resolveDescription(row);
        String lists = row.getString("lists");
        return new EditorialService.Book(
            row.getString("kind"),
            row.getString("book_id"),
            row.getString("title"),
            row.getString("author"),
            row.getString("section"),
            description.text(),
            description.source(),
            lists == null || lists.isBlank() ? List.of() : List.of(lists.split("\n")),
            Objects.requireNonNullElse(row.getString("comment"), ""),
            row.getObject("version") == null ? -1 : row.getLong("version"),
            row.getString("pdf_id"),
            row.getLong("pdf_version"),
            row.getBoolean("custom_cover"),
            row.getString("cover_title"),
            styles.read(row.getString("description_style")),
            styles.read(row.getString("comment_style"))
        );
    }

    private record Description(String text, String source) {}

    private Description resolveDescription(ResultSet row) throws SQLException {
        var summary = BookDescriptions.find(row.getString("title"), row.getString("author"));
        String edited = row.getString("description");
        // An explicit empty editorial value is intentional, not a missing description.
        if (edited != null) {
            return new Description(edited, edited.equals(summary.text()) ? summary.source() : "");
        }
        String original = row.getString("original_description");
        // Supplemental defaults fill empty descriptions only; legacy summaries retain priority.
        if (summary.text().isBlank()
            || (summary.supplemental() && original != null && !original.isBlank())) {
            return new Description(Objects.requireNonNullElse(original, ""), "");
        }
        return new Description(summary.text(), summary.source());
    }
}
