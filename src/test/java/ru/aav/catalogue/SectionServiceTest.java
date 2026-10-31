package ru.aav.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:section-refactor;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "bookstore.admin.email=",
    "bookstore.admin.password="
})
class SectionServiceTest {
    @Autowired SectionService sections;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void prepareIsolatedCatalogue() {
        db.update("DELETE FROM store_sections");
        db.update("INSERT INTO store_sections(id,name,catalog_key,library_key,sort_order,version) VALUES(?,?,?,?,?,?)",
            "legacy", "Своё название", "Legacy", "legacy", 35, 7);
        db.update("INSERT INTO store_sections(id,name,catalog_key,library_key,sort_order) VALUES(?,?,?,?,?)",
            "uncategorized", "Каталог", "Каталог", "uncategorized", 10);
    }

    @Test
    void importReusesOriginalKeyOrRenamedDisplayLabelWithoutChangingTheSection() {
        var before = sections.get("legacy");
        assertThat(sections.ensureCatalog(" Legacy ")).isEqualTo("Legacy");
        assertThat(sections.ensureCatalog("своё название")).isEqualTo("Legacy");
        assertThat(sections.get("legacy")).isEqualTo(before);
        assertThat(sections.list()).hasSize(2);
    }

    @Test
    void blankImportedCategoryUsesTheExistingDefaultSection() {
        assertThat(sections.ensureCatalog(null)).isEqualTo("Каталог");
        assertThat(sections.ensureCatalog("  ")).isEqualTo("Каталог");
        assertThat(sections.list()).hasSize(2);
    }

    @Test
    void importedLongNameKeepsItsFullKeyAndAppendsOneSection() {
        String key = "Импорт " + "а".repeat(90);
        assertThat(sections.ensureCatalog(key)).isEqualTo(key);
        var created = sections.list().stream().filter(s -> s.catalogKey().equals(key)).findFirst().orElseThrow();
        assertThat(created.name()).isEqualTo(key.substring(0, 80));
        assertThat(created.libraryKey()).isEqualTo(created.id().replace("-", ""));
        assertThat(created.position()).isEqualTo(45);
        assertThat(created.version()).isZero();
        assertThat(sections.ensureCatalog(key)).isEqualTo(key);
        assertThat(sections.list()).hasSize(3);
        assertThatThrownBy(() -> sections.ensureCatalog(key + "б"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(sections.list()).hasSize(3);
    }

    @Test
    void manualCreationPreservesIndependentStorageKeysAndSortOrder() {
        var created = sections.create("  Новый раздел  ");
        assertThat(created.name()).isEqualTo("Новый раздел");
        assertThat(created.catalogKey()).isEqualTo("section-" + created.id());
        assertThat(created.libraryKey()).isEqualTo(created.id().replace("-", ""));
        assertThat(created.position()).isEqualTo(45);
        assertThat(created.version()).isZero();
        assertThat(sections.create("Следующий раздел").position()).isEqualTo(55);
    }

    @Test
    void duplicateOrInvalidNamesCannotCreatePartialRecords() {
        assertThatThrownBy(() -> sections.create("СВОЁ НАЗВАНИЕ"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        for (String value : new String[]{" ", "Первая\nВторая", "Первая\tВторая"}) {
            assertThatThrownBy(() -> sections.create(value))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        assertThat(sections.list()).hasSize(2);
    }

    @Test
    void staleUpdatesAndDeletesPreserveTheCurrentRecord() {
        var before = sections.get("legacy");
        assertThatThrownBy(() -> sections.update("legacy", "Подмена", 0, 6))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> sections.delete("legacy", 6))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(sections.get("legacy")).isEqualTo(before);
        var updated = sections.update("legacy", "Переименован", 3, 7);
        assertThat(updated.version()).isEqualTo(8);
        assertThat(updated.catalogKey()).isEqualTo(before.catalogKey());
        assertThat(updated.libraryKey()).isEqualTo(before.libraryKey());
        sections.delete("legacy", 8);
        assertThatThrownBy(() -> sections.get("legacy"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
