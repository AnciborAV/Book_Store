package ru.aav;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.aav.auth.AccountService;
import ru.aav.auth.Registration;
import ru.aav.catalog.ReviewService;
import ru.aav.catalog.SelectionService;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:safety;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class MutationSafetyTest {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired SelectionService selection;
    @Autowired ReviewService reviews;
    @Autowired JdbcTemplate jdbc;

    @Test
    void catalogueRequiresLoginAndCsrf() throws Exception {
        String book = "{\"name\":\"Protected\",\"author\":\"Author\"}";
        mvc.perform(post("/books").with(csrf()).contentType("application/json").content(book))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/books").with(user("reader")).contentType("application/json").content(book))
                .andExpect(status().isForbidden());
        mvc.perform(get("/books")).andExpect(status().isOk());
    }

    @Test
    void rejectsUnicodePasswordExceedingBcryptByteLimit() throws Exception {
        String body = "{\"email\":\"unicode@example.com\",\"displayName\":\"Reader\",\"password\":\"" + "я".repeat(40) + "\"}";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentMutationsRemainUniqueAndValid() throws Exception {
        String email = "concurrent@example.com";
        accounts.register(new Registration(email, "Reader", "test-password"));
        long book = jdbc.queryForObject("SELECT MIN(id) FROM books", Long.class);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var tasks = IntStream.range(0, 8).<Callable<Void>>mapToObj(i -> () -> {
                selection.favorite(email, book);
                selection.quantity(email, book, i + 1);
                reviews.save(book, email, i % 5 + 1, "Review " + i);
                return null;
            }).toList();
            for (var result : pool.invokeAll(tasks, 30, TimeUnit.SECONDS)) result.get();
        }
        assertThat(selection.get(email).favorites()).containsExactly(book);
        assertThat(selection.get(email).cart().get(book)).isBetween(1, 8);
        assertThat(reviews.list(book, email)).hasSize(1);
    }
}
