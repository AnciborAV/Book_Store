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
        "spring.datasource.url=jdbc:h2:mem:selectiontest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
@AutoConfigureMockMvc
class SelectionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void choicesBelongToAccountAndSurviveNewLogin() throws Exception {
        long book = jdbc.queryForObject(
            "SELECT MIN(id) FROM books",
            Long.class
        );
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_favorites",
                Integer.class
            )
        ).isZero();
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM cart_items",
                Integer.class
            )
        ).isZero();
        mvc.perform(get("/api/selection")).andExpect(status().isUnauthorized());
        var first = register("first@example.com");
        var second = register("second@example.com");
        mvc.perform(get("/api/selection").session(first)).andExpect(
            content().json("{\"favorites\":[],\"cart\":{}}")
        );
        mvc.perform(
            put("/api/selection/favorites/" + book).session(first)
        ).andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) mvc.perform(
            put("/api/selection/favorites/" + book)
                .session(first)
                .with(csrf())
        ).andExpect(status().isNoContent());
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_favorites",
                Integer.class
            )
        ).isEqualTo(1);
        mvc.perform(
            put("/api/selection/cart/" + book)
                .session(first)
                .with(csrf())
                .contentType("application/json")
                .content("{\"quantity\":2}")
        ).andExpect(status().isNoContent());
        for (int quantity : new int[] { 0, 100 })
            mvc.perform(
                put("/api/selection/cart/" + book)
                    .session(first)
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"quantity\":" + quantity + "}")
            ).andExpect(status().isBadRequest());
        mvc.perform(
            put("/api/selection/cart/9223372036854775807")
                .session(first)
                .with(csrf())
                .contentType("application/json")
                .content("{\"quantity\":1}")
        ).andExpect(status().isNotFound());
        mvc.perform(
            put("/api/selection/favorites/9223372036854775807")
                .session(first)
                .with(csrf())
        ).andExpect(status().isNotFound());
        mvc.perform(
            delete("/api/selection/cart/" + book)
                .session(second)
                .with(csrf())
        ).andExpect(status().isNoContent());
        mvc.perform(
            delete("/api/selection/favorites/" + book)
                .session(second)
                .with(csrf())
        ).andExpect(status().isNoContent());
        mvc.perform(get("/api/selection").session(second)).andExpect(
            content().json("{\"favorites\":[],\"cart\":{}}")
        );
        mvc.perform(
            post("/api/auth/logout").session(first).with(csrf())
        ).andExpect(status().isNoContent());
        var fresh = login("first@example.com");
        mvc.perform(get("/api/selection").session(fresh)).andExpect(
            content().json(
                "{\"favorites\":[" + book + "],\"cart\":{\"" + book + "\":2}}"
            )
        );
        mvc.perform(
            put("/api/selection/cart/" + book)
                .session(fresh)
                .with(csrf())
                .contentType("application/json")
                .content("{\"quantity\":3}")
        ).andExpect(status().isNoContent());
        assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM cart_items WHERE book_id=?",
                Integer.class,
                book
            )
        ).isEqualTo(3);
        mvc.perform(
            delete("/api/selection/cart").session(second).with(csrf())
        ).andExpect(status().isNoContent());
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM cart_items",
                Integer.class
            )
        ).isEqualTo(1);
        mvc.perform(
            delete("/api/selection/cart").session(fresh).with(csrf())
        ).andExpect(status().isNoContent());
        mvc.perform(
            delete("/api/selection/favorites/" + book)
                .session(fresh)
                .with(csrf())
        ).andExpect(status().isNoContent());
        mvc.perform(get("/api/selection").session(fresh)).andExpect(
            content().json("{\"favorites\":[],\"cart\":{}}")
        );
        assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM books", Integer.class)
        ).isEqualTo(250);
    }

    private MockHttpSession register(String email) throws Exception {
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
        return login(email);
    }

    private MockHttpSession login(String email) throws Exception {
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
