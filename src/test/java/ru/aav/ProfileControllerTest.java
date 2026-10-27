package ru.aav;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import ru.aav.auth.UserRepository;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:profiletest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class ProfileControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;

    @Test void registrationLoginAvatarAndLogout() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        String body="{\"email\":\"Reader@example.com\",\"displayName\":\"Читатель\",\"password\":\"password123\"}";
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isCreated());
        assertThat(users.findByEmail("reader@example.com").orElseThrow().getPasswordHash()).startsWith("$2").isNotEqualTo("password123");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").with(csrf()).param("email","reader@example.com").param("password","wrong-password")).andExpect(status().isUnauthorized());
        var session=(MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("email","reader@example.com").param("password","password123"))
            .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/profile").session(session)).andExpect(jsonPath("$.displayName").value("Читатель")).andExpect(jsonPath("$.hasAvatar").value(false)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var output=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(40,20,BufferedImage.TYPE_INT_RGB),"png",output);
        mvc.perform(multipart("/api/profile/avatar").file(new MockMultipartFile("file","avatar.png","image/png",output.toByteArray()))
            .with(r->{r.setMethod("PUT");return r;}).with(csrf()).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.hasAvatar").value(true));
        byte[] stored=users.findByEmail("reader@example.com").orElseThrow().getAvatar();
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(stored)).getWidth()).isEqualTo(256);
        // A fresh session sees the persisted avatar, not a session-only copy.
        var second=(MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("email","reader@example.com").param("password","password123")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/api/profile/avatar").session(second)).andExpect(status().isOk()).andExpect(content().bytes(stored));
        mvc.perform(multipart("/api/profile/avatar").file(new MockMultipartFile("file","fake.png","image/png","invalid".getBytes()))
            .with(r->{r.setMethod("PUT");return r;}).with(csrf()).session(second)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
    }
}
