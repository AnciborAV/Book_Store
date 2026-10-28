package ru.aav;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import ru.aav.catalog.ReviewService;
import ru.aav.feedback.FeedbackService;
import ru.aav.notifications.ReviewMailQueue;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:reviewnotifications;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","bookstore.review-mail.enabled=false"})
@AutoConfigureMockMvc
class ReviewNotificationsTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 @Autowired ReviewService reviews;
 @Autowired FeedbackService feedback;
 @Autowired ReviewMailQueue queue;
 @Autowired org.springframework.transaction.PlatformTransactionManager manager;
 String email;long book;
 @BeforeEach void setup(){
  jdbc.update("DELETE FROM review_mail");jdbc.update("DELETE FROM site_feedback");jdbc.update("DELETE FROM book_reviews");
  email=UUID.randomUUID()+"@example.test";
  jdbc.update("INSERT INTO store_users(email,display_name,password_hash,role) VALUES(?,?,?,'CUSTOMER')",email,"Читатель","unused");
  book=jdbc.queryForObject("SELECT MIN(id) FROM books",Long.class);
 }
 @Test void bookInboxIsAdminOnlyAndQueuesOnce() throws Exception {
  reviews.save(book,email,5,"Хорошая книга");reviews.save(book,email,4,"Обновлённый отзыв");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_mail",Integer.class)).isEqualTo(1);
  mvc.perform(get("/api/admin/reviews")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/admin/reviews").with(user(email))).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/reviews").with(user("admin").roles("ADMIN")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].rating").value(4));
  long id=jdbc.queryForObject("SELECT id FROM book_reviews",Long.class);
  mvc.perform(post("/api/admin/reviews/"+id+"/read").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/reviews/"+id+"/read").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/admin/reviews?unread=true").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.total").value(0));
  reviews.save(book,email,4,"Ещё одно изменение");
  mvc.perform(get("/api/admin/reviews?unread=true").with(user("admin").roles("ADMIN"))).andExpect(jsonPath("$.total").value(1));
 }
 @Test void privateSiteFeedbackQueuesAndIdempotentRetryDoesNotDuplicate() throws Exception {
  UUID request=UUID.randomUUID();
  feedback.create(email,request,FeedbackService.Kind.IDEA,"Сделайте поиск",false);
  feedback.create(email,request,FeedbackService.Kind.IDEA,"Сделайте поиск",false);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_mail",Integer.class)).isEqualTo(1);
  mvc.perform(get("/api/site-feedback")).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/api/admin/reviews/status").with(user("admin").roles("ADMIN")))
    .andExpect(jsonPath("$.pendingSite").value(1)).andExpect(jsonPath("$.pendingMail").value(1)).andExpect(jsonPath("$.mailConfigured").value(false));
 }
 @Test void rollbackAlsoRollsBackNotificationAndQueueUsesLease(){
  new TransactionTemplate(manager).executeWithoutResult(tx->{reviews.save(book,email,5,"Тест");tx.setRollbackOnly();});
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM review_mail",Integer.class)).isZero();
  reviews.save(book,email,5,"Тест");
  String id=queue.due().getFirst().id();
  assertThat(queue.claim(id)).isTrue();assertThat(queue.claim(id)).isFalse();
  queue.failed(id,"HTTP 503");
  assertThat(queue.due()).isEmpty();
  jdbc.update("UPDATE review_mail SET attempts=5,next_attempt_at=CURRENT_TIMESTAMP WHERE id=?",id);
  assertThat(queue.retryFailed()).isEqualTo(1);assertThat(queue.due()).hasSize(1);
  assertThat(queue.claim(id)).isTrue();queue.sent(id);assertThat(queue.due()).isEmpty();
 }
}
