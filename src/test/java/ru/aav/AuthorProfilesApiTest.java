package ru.aav;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:author-profiles;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class AuthorProfilesApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Test void anonymousReadersCanSeeBothCatalogAndPdfAuthorsWithoutChangingData() throws Exception {
        db.update("INSERT INTO books(name,author,category) VALUES('Профили авторов: тест','Ричард С. Саттон, Эндрю Дж. Барто','AI')");
        long id=db.queryForObject("SELECT id FROM books WHERE name='Профили авторов: тест'",Long.class);
        mvc.perform(get("/api/editorial/books/catalog/"+id+"/authors")).andExpect(status().isOk())
            .andExpect(jsonPath("$.authors.length()").value(2)).andExpect(jsonPath("$.authors[0].name").value("Ричард С. Саттон"))
            .andExpect(jsonPath("$.authors[0].sources").doesNotExist());
        String pdf="00000000-0000-0000-0000-000000000116";
        db.update("INSERT INTO java_library(id,title,author,file_size,page_count,section) VALUES(?,?,?,?,?,?)",pdf,"Human Compatible","Стюарт Рассел",100,1,"ai");
        mvc.perform(get("/api/editorial/books/library/"+pdf+"/authors")).andExpect(status().isOk()).andExpect(jsonPath("$.authors[0].name").value("Стюарт Рассел"));
        mvc.perform(get("/api/editorial/books/catalog/999999999/authors")).andExpect(status().isNotFound());
        mvc.perform(get("/api/editorial/books/wrong/nope/authors")).andExpect(status().isBadRequest());
        mvc.perform(get("/covers/authors/stuart-j-russell.jpg")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("image/jpeg"));
    }
}
