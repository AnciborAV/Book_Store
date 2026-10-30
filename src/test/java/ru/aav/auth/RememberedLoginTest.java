package ru.aav.auth;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:remember;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class RememberedLoginTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired PasswordRecovery recovery;

    String register() throws Exception {
        String email=UUID.randomUUID()+"@example.org";
        mvc.perform(post("/api/auth/register").secure(true).with(csrf()).contentType("application/json")
            .content("{\"email\":\""+email+"\",\"displayName\":\"Reader\",\"password\":\"old-password\",\"remember-me\":\"true\"}"))
            .andExpect(status().isCreated());
        return email;
    }
    MvcResult login(String email, boolean remember) throws Exception {
        return mvc.perform(post("/api/auth/login").secure(true).with(csrf()).param("email",email)
            .param("password","old-password").param("remember-me",String.valueOf(remember)))
            .andExpect(status().isNoContent()).andReturn();
    }
    Cookie cookie(MvcResult result) {
        return MockCookie.parse(result.getResponse().getHeaders("Set-Cookie").stream()
            .filter(h->h.startsWith("bookstore-remember=")).findFirst().orElseThrow());
    }
    @Test void remembersWithoutSessionAndRotatesToken() throws Exception {
        String email=register();var loggedIn=login(email,true);var initial=cookie(loggedIn);
        assertThat(initial.getMaxAge()).isEqualTo(30*24*60*60);
        assertThat(initial.isHttpOnly()).isTrue();assertThat(initial.getSecure()).isTrue();
        assertThat(((MockCookie)initial).getSameSite()).isEqualTo("Lax");
        ((MockHttpSession)loggedIn.getRequest().getSession()).invalidate();
        var restored=mvc.perform(get("/api/profile").secure(true).cookie(initial)).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email)).andReturn();
        assertThat(cookie(restored).getValue()).isNotEqualTo(initial.getValue());
        var session=(MockHttpSession)restored.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/profile").secure(true).session(session)).andExpect(status().isOk());
    }
    @Test void optOutAndFailedLoginDoNotRemember() throws Exception {
        String email=register();var result=login(email,false);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).noneMatch(h->h.startsWith("bookstore-remember="));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE username=?",Integer.class,email)).isZero();
        mvc.perform(post("/api/auth/login").secure(true).with(csrf()).param("email",email).param("password","wrong-password").param("remember-me","true"))
            .andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE username=?",Integer.class,email)).isZero();
    }
    @Test void logoutRevokesRememberedAccess() throws Exception {
        String email=register();var login=login(email,true);var old=cookie(login);
        mvc.perform(post("/api/auth/logout").secure(true).with(csrf()).session((MockHttpSession)login.getRequest().getSession()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/profile").secure(true).cookie(old)).andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE username=?",Integer.class,email)).isZero();
    }
    @Test void expiredAndTamperedTokensFail() throws Exception {
        String email=register();var original=cookie(login(email,true));
        db.update("UPDATE persistent_logins SET last_used=TIMESTAMP '2000-01-01 00:00:00' WHERE username=?",email);
        mvc.perform(get("/api/profile").secure(true).cookie(original)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profile").secure(true).cookie(new Cookie("bookstore-remember","invalid-cookie")))
            .andExpect(status().isUnauthorized());
    }
    @Test void passwordResetRevokesBothCookiesAndRestoredSessions() throws Exception {
        String email=register();var original=cookie(login(email,true));
        var restored=mvc.perform(get("/api/profile").secure(true).cookie(original)).andExpect(status().isOk()).andReturn();
        var remembered=cookie(restored);var session=(MockHttpSession)restored.getRequest().getSession(false);
        String token="a".repeat(43);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        db.update("INSERT INTO password_resets(user_id,token_hash,expires_at,requested_at) SELECT id,?,CURRENT_TIMESTAMP+INTERVAL '10' MINUTE,CURRENT_TIMESTAMP FROM store_users WHERE email=?",hash,email);
        recovery.reset(token,"new-password","new-password");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM persistent_logins WHERE username=?",Integer.class,email)).isZero();
        mvc.perform(get("/api/profile").secure(true).session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/profile").secure(true).cookie(remembered)).andExpect(status().isUnauthorized());
    }
}
