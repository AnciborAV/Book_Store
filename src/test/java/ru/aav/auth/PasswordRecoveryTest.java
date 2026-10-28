package ru.aav.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.mail.host=smtp.example.org","bookstore.review-mail.from=sender@example.org"})
@AutoConfigureMockMvc
class PasswordRecoveryTest {
 @Autowired PasswordRecovery recovery;
 @Autowired JdbcTemplate db;
 @Autowired MockMvc mvc;
 @MockBean JavaMailSender mail;
 String register() throws Exception {
  String email = UUID.randomUUID()+"@example.org";
  mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content("{\"email\":\""+email+"\",\"displayName\":\"Reader\",\"password\":\"old-password\"}")).andExpect(status().isCreated());
  return email;
 }
 String issue(String email) {
  recovery.issue(email);
  var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
  verify(mail).send(captor.capture());
  String text = captor.getValue().getText();
  return text.substring(text.indexOf("#reset-password=")+16).split("\\s")[0];
 }
 @Test void singleUseConfirmationAndSessions() throws Exception {
  String email=register();
  var session=(MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password","old-password")).andExpect(status().isNoContent()).andReturn().getRequest().getSession();
  String token=issue(email);
  assertThat(token).hasSize(43);
  assertThat(db.queryForObject("SELECT token_hash FROM password_resets WHERE user_id=(SELECT id FROM store_users WHERE email=?)",String.class,email)).doesNotContain(token);
  assertThatThrownBy(()->recovery.reset(token,"new-password","different-password")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  recovery.reset(token,"new-password","new-password");
  assertThatThrownBy(()->recovery.reset(token,"another-password","another-password")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  mvc.perform(get("/api/profile").session(session)).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password","old-password")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password","new-password")).andExpect(status().isNoContent());
 }
 @Test void expiryAndCooldown() throws Exception {
  String email=register(); String token=issue(email);
  recovery.issue(email); verify(mail,times(1)).send(any(SimpleMailMessage.class));
  db.update("UPDATE password_resets SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE user_id=(SELECT id FROM store_users WHERE email=?)",email);
  assertThatThrownBy(()->recovery.reset(token,"new-password","new-password")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void csrfAndUnknownAddress() throws Exception {
  mvc.perform(post("/api/auth/password/request").contentType("application/json").content("{\"email\":\"unknown@example.org\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/auth/password/request").with(csrf()).contentType("application/json").content("{\"email\":\"unknown@example.org\"}")).andExpect(status().isNoContent());
  recovery.issue("absent@example.org"); verifyNoInteractions(mail);
 }
 @Test void failedMailDoesNotLeaveToken() throws Exception {
  String email=register(); doThrow(new org.springframework.mail.MailSendException("Unavailable")).when(mail).send(any(SimpleMailMessage.class));
  assertThatThrownBy(()->recovery.issue(email)).isInstanceOf(org.springframework.mail.MailSendException.class);
  assertThat(db.queryForObject("SELECT COUNT(*) FROM password_resets WHERE user_id=(SELECT id FROM store_users WHERE email=?)",Integer.class,email)).isZero();
 }
}
