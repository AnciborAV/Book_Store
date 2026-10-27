package ru.aav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import ru.aav.model.Book;
import ru.aav.repository.BookRepository;

@org.springframework.security.test.context.support.WithMockUser
class BookControllerTest extends IntegrationTest {

    @Autowired
    BookRepository repository;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void clearBooks() {
        repository.deleteAll();
    }

    @Test
    void createsAndReadsBookWithGeneratedIdAndLocation() throws Exception {
        var response = mockMvc
            .perform(
                post("/books")
                    .with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Java\",\"author\":\"Author\"}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Java"))
            .andExpect(jsonPath("$.author").value("Author"))
            .andReturn()
            .getResponse();
        long id = json
            .readTree(response.getContentAsString())
            .get("id")
            .asLong();
        assertThat(id).isPositive();
        assertThat(response.getHeader("Location")).isEqualTo(
            "http://localhost/books/" + id
        );
        mockMvc
            .perform(get("/books/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.name").value("Java"));
        mockMvc
            .perform(get("/books"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void missingBookReturns404() throws Exception {
        mockMvc
            .perform(get("/books/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankOrMissingFields() throws Exception {
        for (String body : new String[] {
            "{\"name\":\" \",\"author\":\"Author\"}",
            "{\"name\":\"Java\"}",
            "{\"name\":\"Java\",\"author\":\" \"}",
        }) {
            mockMvc
                .perform(
                    post("/books")
                        .with(
                            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsMalformedJsonAndNonNumericId() throws Exception {
        mockMvc
            .perform(
                post("/books")
                    .with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{")
            )
            .andExpect(status().isBadRequest());
        mockMvc
            .perform(get("/books/not-a-number"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateNameReturns409() throws Exception {
        var book = new Book();
        book.setName("Java");
        book.setAuthor("Original");
        repository.save(book);
        mockMvc
            .perform(
                post("/books")
                    .with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Java\",\"author\":\"Other\"}")
            )
            .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void suppliedIdCannotOverwriteExistingBook() throws Exception {
        var original = new Book();
        original.setName("Original");
        original.setAuthor("First");
        long id = repository.save(original).getId();
        mockMvc
            .perform(
                post("/books")
                    .with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"id\":" +
                            id +
                            ",\"name\":\"New\",\"author\":\"Second\"}"
                    )
            )
            .andExpect(status().isCreated());
        assertThat(repository.findById(id).orElseThrow().getName()).isEqualTo(
            "Original"
        );
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void identityAndHashRemainCorrectAfterPersistence() {
        var first = new Book();
        first.setName("Identity");
        first.setAuthor("Author");
        var second = new Book();
        assertThat(first).isNotEqualTo(second);
        var books = new HashSet<Book>();
        books.add(first);
        repository.save(first);
        assertThat(first.getId()).isNotNull();
        assertThat(first.getCreated()).isNotNull();
        assertThat(first.getModified()).isNotNull();
        assertThat(books).contains(first);
        assertThat(repository.findById(first.getId()).orElseThrow()).isEqualTo(
            first
        );
    }
}
