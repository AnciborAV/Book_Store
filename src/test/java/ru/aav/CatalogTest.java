package ru.aav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:catalogtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
@AutoConfigureMockMvc
class CatalogTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void catalogAndReviewsRespectIdentityValidationAndPersistence()
        throws Exception {
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM books", Integer.class)
        ).isEqualTo(250);
        assertThat(
            jdbc.queryForList(
                "SELECT COUNT(*) AS n FROM books GROUP BY category",
                Integer.class
            )
        ).containsExactlyInAnyOrder(50, 50, 50, 50, 50);
        long book = jdbc.queryForObject(
            "SELECT MIN(id) FROM books",
            Long.class
        );
        String url = "/api/books/" + book + "/reviews";
        mvc.perform(get("/books"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").isNotEmpty());
        mvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
        mvc.perform(
            put(url)
                .with(csrf())
                .contentType("application/json")
                .content("{\"rating\":5,\"body\":\"Great\"}")
        ).andExpect(status().isUnauthorized());
        var owner = login("owner@example.com");
        var other = login("other@example.com");
        mvc.perform(
            put(url)
                .session(owner)
                .contentType("application/json")
                .content("{\"rating\":5,\"body\":\"Great\"}")
        ).andExpect(status().isForbidden());
        for (String body : new String[] {
            "{\"rating\":6,\"body\":\"Great\"}",
            "{\"rating\":5,\"body\":\" \"}",
        })
            mvc.perform(
                put(url)
                    .session(owner)
                    .with(csrf())
                    .contentType("application/json")
                    .content(body)
            ).andExpect(status().isBadRequest());
        mvc.perform(
            put(url)
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content("{\"rating\":5,\"body\":\"Очень хорошая книга\"}")
        ).andExpect(status().isNoContent());
        mvc.perform(
            put(url)
                .session(owner)
                .with(csrf())
                .contentType("application/json")
                .content("{\"rating\":4,\"body\":\"Изменённый отзыв\"}")
        ).andExpect(status().isNoContent());
        mvc.perform(get(url))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].rating").value(4))
            .andExpect(jsonPath("$[0].own").value(false))
            .andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(get(url).session(owner)).andExpect(
            jsonPath("$[0].own").value(true)
        );
        long review = jdbc.queryForObject(
            "SELECT id FROM book_reviews WHERE book_id=?",
            Long.class,
            book
        );
        mvc.perform(
            delete(url + "/" + review)
                .session(other)
                .with(csrf())
        ).andExpect(status().isNotFound());
        mvc.perform(
            delete(url + "/" + review)
                .session(owner)
                .with(csrf())
        ).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(content().json("[]"));
        mvc.perform(get("/api/books/9223372036854775807/reviews")).andExpect(
            status().isNotFound()
        );
    }

    private MockHttpSession login(String email) throws Exception {
        mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\"" +
                        email +
                        "\",\"displayName\":\"Reader\",\"password\":\"password123\"}"
                )
        ).andExpect(status().isCreated());
        return (MockHttpSession) mvc
            .perform(
                post("/api/auth/login")
                    .with(csrf())
                    .param("email", email)
                    .param("password", "password123")
            )
            .andExpect(status().isNoContent())
            .andReturn()
            .getRequest()
            .getSession(false);
    }
}
