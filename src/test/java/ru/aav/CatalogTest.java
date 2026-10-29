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

    @Test
    void pdfFavoritesArePrivateIdempotentAndRemovedWithBook() throws Exception {
        String javaId = "00000000-0000-0000-0000-000000000091";
        String law = "00000000-0000-0000-0000-000000000092";
        for (String id : java.util.List.of(javaId, law)) {
            jdbc.update("INSERT INTO java_library(id,title,author,file_size,page_count,section) VALUES(?,?,?,?,?,?)",
                id, "Favorite test", "Author", 10, 1, id.equals(javaId) ? "java" : "civil-law");
        }
        mvc.perform(get("/api/java-library/metadata").param("ids", javaId + "," + law))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].pageCount").value(1)).andExpect(jsonPath("$[0].version").value(0));
        mvc.perform(get("/api/java-library/metadata").param("ids", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/java-library/metadata").param("ids", String.join(",", java.util.Collections.nCopies(13, javaId))))
            .andExpect(status().isBadRequest());
        var owner = login("pdffavorite@example.com");
        var other = login("otherpdffavorite@example.com");
        String url = "/api/selection/library-favorites/" + javaId;
        mvc.perform(put(url).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put(url).session(owner)).andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) mvc.perform(put(url).session(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put("/api/selection/library-favorites/" + law).session(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/selection").session(owner)).andExpect(jsonPath("$.libraryFavorites.length()").value(2));
        mvc.perform(get("/api/selection").session(other)).andExpect(jsonPath("$.libraryFavorites.length()").value(0));
        mvc.perform(delete(url).session(other).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/selection").session(owner)).andExpect(jsonPath("$.libraryFavorites.length()").value(2));
        for (int i = 0; i < 2; i++) mvc.perform(delete(url).session(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/selection").session(owner)).andExpect(jsonPath("$.libraryFavorites[0]").value(law));
        jdbc.update("DELETE FROM java_library WHERE id=?", law);
        mvc.perform(get("/api/selection").session(owner)).andExpect(jsonPath("$.libraryFavorites.length()").value(0));
        mvc.perform(put("/api/selection/library-favorites/" + law).session(owner).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put("/api/selection/library-favorites/not-a-uuid").session(owner).with(csrf())).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM java_library WHERE id=?", javaId);
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
