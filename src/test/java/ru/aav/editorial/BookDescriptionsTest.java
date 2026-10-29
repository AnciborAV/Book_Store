package ru.aav.editorial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.assertj.core.api.Assertions.assertThat;

class BookDescriptionsTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void everyJavaBookInThePublicCatalogHasASourcedDescription() throws Exception {
        try (var input = getClass().getResourceAsStream("/editorial/java-catalog.json")) {
            var books = json.readTree(input);
            assertThat(books.size()).isEqualTo(24);
            int added = 0;
            for (var book : books) {
                var result = BookDescriptions.find(book.get("title").asText(), book.get("author").asText());
                assertThat(result.text()).as(book.get("title").asText()).isNotBlank();
                var source = URI.create(result.source());
                assertThat(source.getScheme()).isEqualTo("https");
                assertThat(source.getHost()).isNotBlank();
                if (result.supplemental()) {
                    added++;
                    assertThat(result.text()).hasSizeLessThan(1600).doesNotContain("—", "–", "\u00ad");
                }
            }
            assertThat(added).isEqualTo(22);
        }
    }

    @Test void existingEckelAndSchildtDescriptionsRemainExactlyUnchanged() throws Exception {
        try (var input = getClass().getResourceAsStream("/editorial/protected-descriptions.json")) {
            var books = json.readTree(input);
            assertThat(books.size()).isEqualTo(2);
            for (var book : books) {
                var result = BookDescriptions.find(book.get("title").asText(), book.get("author").asText());
                assertThat(result.text()).isEqualTo(book.get("description").asText());
                assertThat(result.source()).isEqualTo(book.get("source").asText());
            }
        }
    }

    @Test void anUnrelatedBookDoesNotReceiveAnotherAuthorsDescription() {
        assertThat(BookDescriptions.find("PostgreSQL изнутри", "Другой автор").text()).isEmpty();
        assertThat(BookDescriptions.find("Неизвестная книга", "Егор Рогов").text()).isEmpty();
        assertThat(BookDescriptions.find("  POSTGRESQL ИЗНУТРИ ", " Егор Рогов ").text()).isNotBlank();
    }
}
