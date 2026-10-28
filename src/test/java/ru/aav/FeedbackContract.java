package ru.aav;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class FeedbackContract {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    String email;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM site_feedback");
        email = UUID.randomUUID()+"@example.test";
        jdbc.update("INSERT INTO store_users(email,display_name,password_hash,role) VALUES(?,?,?,'CUSTOMER')",email,"Читатель","unused");
    }
    String body(UUID key, boolean consent) throws Exception {
        return json.writeValueAsString(Map.of("requestId",key,"kind","IDEA","body","<script>alert(1)</script> Идея","publicConsent",consent));
    }
    String create(boolean consent) throws Exception {
        var response=mvc.perform(post("/api/site-feedback").with(user(email)).with(csrf()).contentType("application/json").content(body(UUID.randomUUID(),consent)))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("id").asText();
    }
    @Test void publicationNeedsConsentAndAdminAndDoesNotExposePrivateData() throws Exception {
        String id=create(true);
        mvc.perform(get("/api/site-feedback")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/admin/site-feedback").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].author").value("Читатель"));
        mvc.perform(patch("/api/admin/site-feedback/"+id).with(user("admin").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"version\":0,\"state\":\"PUBLISHED\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/site-feedback")).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].email").doesNotExist()).andExpect(jsonPath("$.items[0].userId").doesNotExist());
        mvc.perform(patch("/api/admin/site-feedback/"+id).with(user("admin").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"version\":0,\"state\":\"ARCHIVED\"}"))
            .andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/site-feedback/"+id).with(user("admin").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"version\":1,\"state\":\"ARCHIVED\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/site-feedback")).andExpect(jsonPath("$.total").value(0));
    }
    @Test void privateMessagesCannotBePublishedEvenByAdmin() throws Exception {
        String id=create(false);
        mvc.perform(patch("/api/admin/site-feedback/"+id).with(user("admin").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"version\":0,\"state\":\"PUBLISHED\"}"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/site-feedback")).andExpect(jsonPath("$.total").value(0));
    }
    @Test void authenticationCsrfValidationAndRateLimit() throws Exception {
        String payload=body(UUID.randomUUID(),true);
        mvc.perform(post("/api/site-feedback").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/site-feedback").with(user(email)).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/site-feedback").with(user(email))).andExpect(status().isForbidden());
        mvc.perform(post("/api/site-feedback").with(user(email)).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"kind","REVIEW","body"," ")))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/site-feedback").with(user(email)).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isCreated());
        mvc.perform(post("/api/site-feedback").with(user(email)).with(csrf()).contentType("application/json").content(payload)).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM site_feedback",Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/site-feedback").with(user(email)).with(csrf()).contentType("application/json").content(body(UUID.randomUUID(),false))).andExpect(status().isTooManyRequests());
        String id=jdbc.queryForObject("SELECT id FROM site_feedback",String.class);
        mvc.perform(patch("/api/admin/site-feedback/"+id).with(user(email)).with(csrf()).contentType("application/json").content("{\"version\":0,\"state\":\"PUBLISHED\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/site-feedback/"+id).with(user("admin").roles("ADMIN")).contentType("application/json").content("{\"version\":0,\"state\":\"PUBLISHED\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/site-feedback?page=-1")).andExpect(status().isBadRequest());
    }
    @Test void paginationDoesNotLeakPendingMessages() throws Exception {
        for(int i=0;i<22;i++) jdbc.update("INSERT INTO site_feedback(id,user_id,request_id,author,kind,body,public_consent,state) SELECT ?,id,?,'Автор','REVIEW','Отзыв',TRUE,'PUBLISHED' FROM store_users WHERE email=?",UUID.randomUUID().toString(),UUID.randomUUID().toString(),email);
        jdbc.update("UPDATE site_feedback SET created_at=?", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(120)));
        create(false);
        mvc.perform(get("/api/site-feedback")).andExpect(jsonPath("$.total").value(22)).andExpect(jsonPath("$.items.length()").value(20));
        mvc.perform(get("/api/site-feedback?page=1")).andExpect(jsonPath("$.items.length()").value(2));
    }
}
