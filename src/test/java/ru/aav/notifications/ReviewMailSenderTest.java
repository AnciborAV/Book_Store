package ru.aav.notifications;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ReviewMailSenderTest {
 @Test void sendsToBothRecipientsAndAcknowledgesOnlySuccess() {
  var queue=mock(ReviewMailQueue.class);var smtp=mock(JavaMailSender.class);
  when(queue.due()).thenReturn(List.of(new ReviewMailQueue.Mail("one","Новый отзыв","Текст")));
  when(queue.claim("one")).thenReturn(true);
  var sender=new ReviewMailSender(queue,smtp,true,"sender@example.com","work@example.com, personal@example.com","smtp.example.com");
  sender.sendPending();
  var capture=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
  verify(smtp).send(capture.capture());
  assertArrayEquals(new String[]{"work@example.com","personal@example.com"},capture.getValue().getTo());
  assertEquals("Текст",capture.getValue().getText());verify(queue).sent("one");
 }
 @Test void failureRemainsQueued() {
  var queue=mock(ReviewMailQueue.class);var smtp=mock(JavaMailSender.class);
  when(queue.due()).thenReturn(List.of(new ReviewMailQueue.Mail("one","Subject","Body")));
  when(queue.claim("one")).thenReturn(true);
  doThrow(new MailSendException("secret provider details")).when(smtp).send(any(SimpleMailMessage.class));
  new ReviewMailSender(queue,smtp,true,"sender@example.com","work@example.com","smtp.example.com").sendPending();
  verify(queue,never()).sent(anyString());verify(queue).failed(eq("one"),not(contains("secret")));
 }
 @Test void invalidRecipientPreventsSending() {
  var queue=mock(ReviewMailQueue.class);var smtp=mock(JavaMailSender.class);
  var sender=new ReviewMailSender(queue,smtp,true,"sender@example.com","work@example.com,\nBcc:evil@example.com","smtp.example.com");
  assertFalse(sender.configured());sender.sendPending();verifyNoInteractions(queue,smtp);
 }
}
