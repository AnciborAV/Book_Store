package ru.aav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.aav.model.Book;
import ru.aav.repository.BookRepository;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:editorialdto;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class EditorialDtoTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired BookRepository books;
    final String library="00000000-0000-0000-0000-000000000081";
    long catalog;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM book_editorial");
        jdbc.update("DELETE FROM books WHERE name=?","Новая книга каталога");
        jdbc.update("DELETE FROM java_library WHERE id=?",library);
        jdbc.update("INSERT INTO java_library(id,title,author,description,file_size,page_count,section,custom_cover) VALUES(?,?,?,?,?,?,?,?)",
            library,"Новая книга PDF","Автор PDF","Авторское описание PDF",1234,2,"java",false);
        var book=new Book(); book.setName("Новая книга каталога");
        catalog=books.saveAndFlush(book).getId();
    }

    @Test void absentEditorialHasStableDtoDefaultsAcrossBothSources() throws Exception {
        var catalogResponse=mvc.perform(get("/api/editorial/books/catalog/"+catalog))
            .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("catalog"))
            .andExpect(jsonPath("$.id").value(String.valueOf(catalog)))
            .andExpect(jsonPath("$.author").value(""))
            .andExpect(jsonPath("$.description").value(""))
            .andExpect(jsonPath("$.source").value(""))
            .andExpect(jsonPath("$.lists").isEmpty()).andExpect(jsonPath("$.comment").value(""))
            .andExpect(jsonPath("$.version").value(-1))
            .andExpect(jsonPath("$.pdfId").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.pdfVersion").value(0)).andExpect(jsonPath("$.customCover").value(false))
            .andExpect(jsonPath("$.coverTitle").value("Новая книга каталога"))
            .andExpect(jsonPath("$.descriptionStyle").isEmpty()).andExpect(jsonPath("$.commentStyle").isEmpty())
            .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(catalogResponse).size()).isEqualTo(16);
        mvc.perform(get("/api/editorial/books/library/"+library))
            .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("library"))
            .andExpect(jsonPath("$.description").value("Авторское описание PDF"))
            .andExpect(jsonPath("$.version").value(-1)).andExpect(jsonPath("$.pdfId").value(library))
            .andExpect(jsonPath("$.pdfVersion").value(0)).andExpect(jsonPath("$.customCover").value(false))
            .andExpect(jsonPath("$.coverTitle").value("Новая книга PDF"));
        mvc.perform(get("/api/editorial/books")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void explicitEmptyDescriptionNeverRestoresFallback() throws Exception {
        for(String key:List.of("catalog/"+catalog,"library/"+library)) {
            var body=Map.of("version",-1,"description","","comment","Комментарий","lists",List.of("Подборка"));
            mvc.perform(put("/api/admin/editorial/books/"+key).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk());
            mvc.perform(get("/api/editorial/books/"+key))
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.source").value(""))
                .andExpect(jsonPath("$.comment").value("Комментарий"))
                .andExpect(jsonPath("$.lists[0]").value("Подборка"));
        }
    }

    @Test void normalizationPreservesListOrderAndFormattingOffsets() throws Exception {
        jdbc.update("INSERT INTO book_editorial(catalog_id,description,lists,comment) VALUES(?,?,?,?)",
            catalog,"Описание","На главной позже\nДругая подборка","Прежний комментарий");
        var body=Map.of("version",-1,"description","  Текст  ","comment","  Комментарий  ",
            "lists",List.of(" На главной ","Новая","Новая"),
            "descriptionStyle",List.of(Map.of("start",2,"end",7,"bold",true)));
        mvc.perform(put("/api/admin/editorial/books/library/"+library).with(user("admin").roles("ADMIN")).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lists.length()").value(2))
            .andExpect(jsonPath("$.lists[0]").value("На главной"))
            .andExpect(jsonPath("$.lists[1]").value("Новая"))
            .andExpect(jsonPath("$.description").value("Текст"))
            .andExpect(jsonPath("$.comment").value("Комментарий"))
            .andExpect(jsonPath("$.descriptionStyle[0].start").value(0))
            .andExpect(jsonPath("$.descriptionStyle[0].end").value(5));
        mvc.perform(get("/api/editorial/books/catalog/"+catalog))
            .andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.lists[0]").value("На главной позже"));
    }

    @Test void invalidFormattingDoesNotReplaceExistingHomeBook() throws Exception {
        jdbc.update("INSERT INTO book_editorial(catalog_id,description,lists,comment) VALUES(?,?,?,?)",
            catalog,"Описание","На главной","Прежняя рекомендация");
        var body=Map.of("version",-1,"description","Текст","comment","Комментарий",
            "lists",List.of("На главной"),"descriptionStyle",List.of(Map.of("start",0,"end",100,"bold",true)));
        mvc.perform(put("/api/admin/editorial/books/library/"+library).with(user("admin").roles("ADMIN")).with(csrf())
            .contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/editorial/books/catalog/"+catalog))
            .andExpect(jsonPath("$.lists[0]").value("На главной")).andExpect(jsonPath("$.version").value(0));
        mvc.perform(get("/api/editorial/books/library/"+library)).andExpect(jsonPath("$.version").value(-1));
    }
}
