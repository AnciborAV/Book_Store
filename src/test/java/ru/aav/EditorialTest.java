package ru.aav;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:editorial;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class EditorialTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final String library="00000000-0000-0000-0000-000000000071";
    long catalog;
    @BeforeEach void reset() {
        jdbc.update("DELETE FROM book_editorial");
        jdbc.update("DELETE FROM java_library WHERE id=?",library);
        jdbc.update("INSERT INTO java_library(id,title,author,description,file_size,page_count,section) VALUES(?,?,?,?,?,?,?)",
            library,"Философия Java","Брюс Эккель","",100,10,"java");
        catalog=jdbc.queryForObject("SELECT MIN(id) FROM books",Long.class);
    }
    String payload(long version,String lists,String comment) throws Exception {
        return json.writeValueAsString(java.util.Map.of("version",version,"description","Моё описание", "lists", lists.isBlank()?java.util.List.of():java.util.List.of(lists,"Изучаем Java"),"comment",comment));
    }
    @Test void pinnedCommentCanExceedOnePrintedPageWithoutTruncation()throws Exception {
        String comment="Подробный комментарий редактора о книге. ".repeat(350)+"\n\nЗаключительный абзац.";
        for(String key:java.util.List.of("library/"+library,"catalog/"+catalog)) {
            mvc.perform(put("/api/admin/editorial/books/"+key).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload(-1,"Выбор редакции",comment)))
                .andExpect(status().isOk());
            mvc.perform(get("/api/editorial/books/"+key)).andExpect(status().isOk()).andExpect(jsonPath("$.comment").value(comment));
            String edited=comment+" Дополнение.";
            mvc.perform(put("/api/admin/editorial/books/"+key).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload(0,"Выбор редакции",edited))).andExpect(status().isOk());
            mvc.perform(get("/api/editorial/books/"+key)).andExpect(jsonPath("$.comment").value(edited));
        }
    }
    @Test void allHomeStylesheetsArePublic() throws Exception {
        String html = new String(new org.springframework.core.io.ClassPathResource("static/index.html")
            .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var links = java.util.regex.Pattern.compile("href=\"(/[^\"]+\\.css)\"").matcher(html);
        int count = 0;
        while (links.find()) {
            mvc.perform(get(links.group(1))).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
            count++;
        }
        assertThat(count).isGreaterThan(0);
        mvc.perform(get("/reading-text.css")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString(".editorial-note p")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("text-align: justify")));
    }
    @Test void recommendationsPersistAcrossCatalogsAndRemovalDoesNotDeleteBooks() throws Exception {
        mvc.perform(get("/api/editorial/books")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        String url="/api/admin/editorial/books/library/"+library;
        mvc.perform(put(url).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload(-1,"Начать здесь","Рекомендую")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(put("/api/admin/editorial/books/catalog/"+catalog).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload(-1,"Классика","Мой выбор"))).andExpect(status().isOk());
        mvc.perform(get("/api/editorial/books")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/editorial/books/library/"+library)).andExpect(jsonPath("$.lists.length()").value(2)).andExpect(jsonPath("$.comment").value("Рекомендую"));
        mvc.perform(put(url).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload(0,"",""))).andExpect(status().isOk());
        mvc.perform(get("/api/editorial/books")).andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM java_library WHERE id=?",Integer.class,library)).isEqualTo(1);
        jdbc.update("DELETE FROM java_library WHERE id=?",library);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM book_editorial WHERE library_id=?",Integer.class,library)).isZero();
    }
    @Test void mutationsRequireAdminCsrfAndCurrentVersion() throws Exception {
        String url="/api/admin/editorial/books/library/"+library, data=payload(-1,"Выбор редакции","Комментарий");
        mvc.perform(put(url).with(csrf()).contentType("application/json").content(data)).andExpect(status().isUnauthorized());
        mvc.perform(put(url).with(user("reader")).with(csrf()).contentType("application/json").content(data)).andExpect(status().isForbidden());
        mvc.perform(put(url).with(user("admin").roles("ADMIN")).contentType("application/json").content(data)).andExpect(status().isForbidden());
        mvc.perform(put(url).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(data)).andExpect(status().isOk());
        mvc.perform(put(url).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(data)).andExpect(status().isConflict());
        mvc.perform(put(url).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload(0,"Bad\nname",""))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/editorial/books/wrong/12")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/editorial/books/library/00000000-0000-0000-0000-000000000072")).andExpect(status().isNotFound());
    }
    @Test void newDefaultsPreserveBothOriginalAndEditorialDescriptions() throws Exception {
        jdbc.update("UPDATE java_library SET title='PostgreSQL изнутри',author='Егор Рогов' WHERE id=?",library);
        String url="/api/editorial/books/library/"+library;
        mvc.perform(get(url)).andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString("Егор Рогов")))
            .andExpect(jsonPath("$.source").value("https://postgrespro.ru/education/books/internals"));
        jdbc.update("UPDATE java_library SET description='Описание владельца PDF' WHERE id=?",library);
        mvc.perform(get(url)).andExpect(jsonPath("$.description").value("Описание владельца PDF"))
            .andExpect(jsonPath("$.source").value(""));
        jdbc.update("INSERT INTO book_editorial(library_id,description,lists,comment) VALUES(?,?,?,?)",
            library,"Описание редактора","Моя подборка","Мой комментарий");
        mvc.perform(get(url)).andExpect(jsonPath("$.description").value("Описание редактора"))
            .andExpect(jsonPath("$.comment").value("Мой комментарий"))
            .andExpect(jsonPath("$.lists[0]").value("Моя подборка"))
            .andExpect(jsonPath("$.source").value(""));
        jdbc.update("UPDATE book_editorial SET description='' WHERE library_id=?",library);
        mvc.perform(get(url)).andExpect(jsonPath("$.description").value(""));
    }
    @Test void sourcedDescriptionsAreTargetedAndCanBeOverridden() throws Exception {
        mvc.perform(get("/api/editorial/books/library/"+library)).andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString("Брюса Эккеля")))
            .andExpect(jsonPath("$.source").value(org.hamcrest.Matchers.containsString("piter.com")));
        jdbc.update("UPDATE java_library SET title='Java. Полное руководство (12 издание)',author='Герберт Шилдт' WHERE id=?",library);
        mvc.perform(get("/api/editorial/books/library/"+library)).andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.containsString("Java SE 17")));
        jdbc.update("UPDATE java_library SET title='Другая книга',description='Прежнее описание' WHERE id=?",library);
        mvc.perform(get("/api/editorial/books/library/"+library)).andExpect(jsonPath("$.description").value("Прежнее описание")).andExpect(jsonPath("$.source").value(""));
        mvc.perform(put("/api/admin/editorial/books/library/"+library).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload(-1,"",""))).andExpect(jsonPath("$.description").value("Моё описание"));
        mvc.perform(get("/editorial.js")).andExpect(status().isOk());
        mvc.perform(get("/editorial.css")).andExpect(status().isOk());
    }
}
