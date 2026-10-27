package ru.aav;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import ru.aav.model.Book;
import ru.aav.repository.BookRepository;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Сквозные проверки изоляции аккаунтов, поиска и обязательной защиты изменений. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shelftest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class ShelfControllerTest {
    @Autowired MockMvc mvc;
    @Autowired BookRepository books;

    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", "password123"))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private MockHttpSession register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"displayName\":\"Reader\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
        return login(email);
    }

    @Test
    void shelfSurvivesNewSessionAndIsPrivate() throws Exception {
        var first = register("first@example.com");
        var second = register("second@example.com");
        var book = new Book(); book.setName("Searchable Java"); book.setAuthor("Test Author");
        long id = books.save(book).getId();
        mvc.perform(put("/api/shelf/" + id).session(first)).andExpect(status().isForbidden());
        mvc.perform(put("/api/shelf/" + id).session(first).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put("/api/shelf/" + id).session(first).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/shelf").session(login("first@example.com"))).andExpect(jsonPath("$[0]").value(id))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/shelf").session(second)).andExpect(content().json("[]"));
        mvc.perform(get("/api/shelf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/books/page").param("query", "java").param("favorites", "true").session(first))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/books/page").param("favorites", "true").session(second))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(delete("/api/shelf/" + id).session(second).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/shelf").session(first)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/shelf/" + id).session(first).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/shelf").session(first)).andExpect(content().json("[]"));
        mvc.perform(put("/api/shelf/9223372036854775807").session(first).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void catalogueIsPublicButMutationsNeedLoginAndCsrf() throws Exception {
        mvc.perform(get("/books/page")).andExpect(status().isOk());
        mvc.perform(get("/books/page").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/books/page").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/books/page").param("favorites", "true")).andExpect(status().isUnauthorized());
        mvc.perform(post("/books").with(csrf()).contentType("application/json")
                .content("{\"name\":\"Book\",\"author\":\"Author\"}")).andExpect(status().isUnauthorized());
    }
}
