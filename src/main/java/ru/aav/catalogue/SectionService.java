package ru.aav.catalogue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SectionService {
    private static final int MAX_NAME_LENGTH = 80;

    private final JdbcTemplate db;

    public record Section(
        String id,
        String name,
        String catalogKey,
        String libraryKey,
        int position,
        long version
    ) {}

    @Transactional(readOnly = true)
    public List<Section> list() {
        return db.query("SELECT * FROM store_sections ORDER BY sort_order,name,id", this::row);
    }

    @Transactional(readOnly = true)
    public Section get(String id) {
        return db.query("SELECT * FROM store_sections WHERE id=?", this::row, id)
            .stream()
            .findFirst()
            .orElseThrow(SectionService::missing);
    }

    public Section lock(String id) {
        if (db.queryForList("SELECT id FROM store_sections WHERE id=? FOR UPDATE", id).isEmpty()) {
            throw missing();
        }
        return get(id);
    }

    @Transactional
    public String ensureCatalog(String category) {
        lockNames();
        String catalogKey = category == null || category.isBlank() ? "Каталог" : category.strip();
        var existing = db.query(
            "SELECT * FROM store_sections WHERE catalog_key=? OR LOWER(name)=LOWER(?)",
            this::row,
            catalogKey,
            catalogKey
        );
        if (!existing.isEmpty()) {
            return existing.getFirst().catalogKey();
        }

        String id = UUID.randomUUID().toString();
        String name = catalogKey.length() > MAX_NAME_LENGTH
            ? catalogKey.substring(0, MAX_NAME_LENGTH)
            : catalogKey;
        appendSection(id, validatedName(name, ""), catalogKey);
        return catalogKey;
    }

    @Transactional
    public Section create(String value) {
        lockNames();
        String id = UUID.randomUUID().toString();
        String name = validatedName(value, "");
        appendSection(id, name, "section-" + id);
        return get(id);
    }

    @Transactional
    public Section update(String id, String value, int position, long version) {
        lockNames();
        lockCurrentVersion(id, version);
        db.update(
            "UPDATE store_sections SET name=?,sort_order=?,version=version+1 WHERE id=?",
            validatedName(value, id),
            position,
            id
        );
        return get(id);
    }

    @Transactional
    public void delete(String id, long version) {
        lockNames();
        Section section = lockCurrentVersion(id, version);
        if (bookCount(section) > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Сначала перенесите или удалите книги из раздела"
            );
        }
        db.update("DELETE FROM store_sections WHERE id=?", id);
    }

    // Keep the shared catalogue lock before section row locks in every mutation.
    private void lockNames() {
        db.queryForList("SELECT id FROM catalogue_state WHERE id=1 FOR UPDATE");
    }

    private Section lockCurrentVersion(String id, long version) {
        Section section = lock(id);
        if (section.version() != version) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Раздел уже изменён. Обновите список."
            );
        }
        return section;
    }

    private String validatedName(String value, String exceptId) {
        String name = value.strip();
        if (name.isBlank() || name.chars().anyMatch(Character::isISOControl)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Введите название раздела без переносов строк"
            );
        }
        int duplicates = db.queryForObject(
            "SELECT COUNT(*) FROM store_sections WHERE LOWER(name)=LOWER(?) AND id<>?",
            Integer.class,
            name,
            exceptId
        );
        if (duplicates > 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Раздел с таким названием уже существует"
            );
        }
        return name;
    }

    private void appendSection(String id, String name, String catalogKey) {
        int position = db.queryForObject(
            "SELECT COALESCE(MAX(sort_order),0)+10 FROM store_sections",
            Integer.class
        );
        db.update(
            "INSERT INTO store_sections(id,name,catalog_key,library_key,sort_order) VALUES(?,?,?,?,?)",
            id,
            name,
            catalogKey,
            id.replace("-", ""),
            position
        );
    }

    private int bookCount(Section section) {
        int catalogueBooks = db.queryForObject(
            "SELECT COUNT(*) FROM books WHERE category=? AND archived=FALSE",
            Integer.class,
            section.catalogKey()
        );
        int standaloneFiles = db.queryForObject(
            "SELECT COUNT(*) FROM java_library b WHERE section=? AND NOT EXISTS(SELECT 1 FROM catalog_pdf p WHERE p.library_id=b.id)",
            Integer.class,
            section.libraryKey()
        );
        return catalogueBooks + standaloneFiles;
    }

    private Section row(ResultSet result, int rowNumber) throws SQLException {
        return new Section(
            result.getString("id"),
            result.getString("name"),
            result.getString("catalog_key"),
            result.getString("library_key"),
            result.getInt("sort_order"),
            result.getLong("version")
        );
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Раздел не найден");
    }
}
