package ru.aav.commerce;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class OrderMailTest {

    private CommerceDto.Detail detail() {
        return new CommerceDto.Detail(
            new CommerceDto.Order(
                "order-123",
                1,
                "Андрей <script>",
                "buyer@example.com",
                OrderStatus.CREATED,
                new BigDecimal("24.68"),
                "RUB",
                Instant.parse("2026-10-27T12:00:00Z"),
                Instant.now()
            ),
            List.of(
                new CommerceDto.Item(
                    1,
                    "Книга <img src=x>",
                    "Автор & имя",
                    2,
                    new BigDecimal("12.34")
                )
            ),
            List.of()
        );
    }

    @Test
    void brandedMultipartContentIncludesSnapshotPricesAndEscapesHtml() {
        var content = OrderMailTemplate.render(detail());
        assertThat(content.html())
            .contains(
                "переплёт",
                "24,68 ₽",
                "12,34 ₽",
                "Книга &lt;img src=x&gt;",
                "Автор &amp; имя",
                "С уважением",
                "27.10.2026 15:00"
            )
            .doesNotContain("<script>", "<img src=x>");
        assertThat(content.text()).contains(
            "2 × 12,34 ₽ = 24,68 ₽",
            "Команда книжного магазина «Переплёт»"
        );
    }

    @Test
    void smtpFailureKeepsNotificationPendingAndSuccessfulRetryMarksSent()
        throws Exception {
        var queue = mock(OrderMailQueue.class);
        var repository = mock(CommerceRepository.class);
        var smtp = mock(JavaMailSender.class);
        var d = detail();
        when(queue.due()).thenReturn(List.of("order-123"));
        when(queue.claim("order-123")).thenReturn(true);
        when(repository.find("order-123", false)).thenReturn(
            Optional.of(d.order())
        );
        when(repository.items("order-123")).thenReturn(d.items());
        when(repository.events("order-123")).thenReturn(List.of());
        when(smtp.createMimeMessage()).thenAnswer(call ->
            new MimeMessage((Session) null)
        );
        doThrow(new MailSendException("offline"))
            .doNothing()
            .when(smtp)
            .send(any(MimeMessage.class));
        var worker = new OrderMailSender(
            queue,
            repository,
            smtp,
            "shop@example.com"
        );
        worker.sendPending();
        verify(queue, never()).sent(anyString());
        worker.sendPending();
        verify(queue).sent("order-123");
        var messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(smtp, times(2)).send(messages.capture());
        var message = messages.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(
            "buyer@example.com"
        );
        assertThat(message.getSubject()).contains("Переплёт", "order-123");
        message.saveChanges();
        assertThat(message.getContentType()).contains("multipart");
        when(queue.claim("order-123")).thenReturn(false);
        worker.sendPending();
        verify(smtp, times(2)).send(any(MimeMessage.class));
    }
}
