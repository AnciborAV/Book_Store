package ru.aav;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:announcement;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class AnnouncementTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Test void formattedDraftRemainsPrivateAndCanBePublished()throws Exception {
  jdbc.update("UPDATE site_announcement SET body='',body_style='[]',published=FALSE,version=0 WHERE id=1");
  String payload="{\"body\":\"Hello\",\"published\":false,\"version\":0,\"bodyStyle\":[{\"start\":0,\"end\":5,\"bold\":true,\"size\":24}]}";
  mvc.perform(put("/api/admin/announcement").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isNoContent());
  mvc.perform(get("/api/announcement")).andExpect(jsonPath("$.body").value("")).andExpect(jsonPath("$.bodyStyle.length()").value(0));
  mvc.perform(get("/api/admin/announcement").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.bodyStyle[0].size").value(24));
  mvc.perform(put("/api/admin/announcement").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"body\":\"Hello\",\"published\":true,\"version\":1}")).andExpect(status().isNoContent());
  mvc.perform(get("/api/announcement")).andExpect(jsonPath("$.bodyStyle[0].bold").value(true));
 }
 @Test void adminPublicationIsProtectedAndHiddenTextStaysPrivate() throws Exception {
  jdbc.update("UPDATE site_announcement SET body='',published=FALSE,version=0 WHERE id=1");
  String payload="{\"body\":\"Welcome\",\"published\":true,\"version\":0}";
  mvc.perform(put("/api/admin/announcement").with(user("reader")).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/announcement").with(user("admin").roles("ADMIN")).contentType("application/json").content(payload)).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/announcement").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isNoContent());
  mvc.perform(get("/api/announcement")).andExpect(status().isOk()).andExpect(jsonPath("$.body").value("Welcome"));
  mvc.perform(put("/api/admin/announcement").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isConflict());
  mvc.perform(put("/api/admin/announcement").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"body\":\"Welcome\",\"published\":false,\"version\":1}")).andExpect(status().isNoContent());
  mvc.perform(get("/api/announcement")).andExpect(jsonPath("$.body").value("")).andExpect(jsonPath("$.published").value(false));
  mvc.perform(get("/api/admin/announcement").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.body").value("Welcome"));
 }
}
