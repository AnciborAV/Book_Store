package ru.aav.commerce;

import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** SMTP runs outside checkout transactions; failures cannot roll back a purchase. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "bookstore.mail.enabled", havingValue = "true")
public class OrderMailSender {

    private static final Logger log = LoggerFactory.getLogger(
        OrderMailSender.class
    );
    private final OrderMailQueue queue;
    private final CommerceRepository orders;
    private final JavaMailSender sender;
    private final String from;

    public OrderMailSender(
        OrderMailQueue queue,
        CommerceRepository orders,
        JavaMailSender sender,
        @Value("${bookstore.mail.from:}") String from
    ) {
        this.queue = queue;
        this.orders = orders;
        this.sender = sender;
        this.from = from;
        try {
            if (from.isBlank()) throw new IllegalArgumentException();
            new InternetAddress(from, true).validate();
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                "Set MAIL_FROM to a valid sender address when BOOKSTORE_MAIL_ENABLED=true"
            );
        }
    }

    @Scheduled(
        fixedDelayString = "${bookstore.mail.poll-ms:15000}",
        initialDelayString = "${bookstore.mail.initial-delay-ms:15000}"
    )
    public void sendPending() {
        for (String id : queue.due()) {
            if (!queue.claim(id)) continue;
            try {
                var order = orders.find(id, false).orElseThrow();
                var content = OrderMailTemplate.render(
                    new CommerceDto.Detail(
                        order,
                        orders.items(id),
                        orders.events(id)
                    )
                );
                var message = sender.createMimeMessage();
                var helper = new MimeMessageHelper(
                    message,
                    true,
                    StandardCharsets.UTF_8.name()
                );
                helper.setFrom(
                    new InternetAddress(
                        from,
                        "Переплёт",
                        StandardCharsets.UTF_8.name()
                    )
                );
                helper.setTo(order.buyerEmail());
                helper.setSubject(content.subject());
                helper.setText(content.text(), content.html());
                sender.send(message);
                queue.sent(id);
            } catch (Exception ex) {
                // Do not log SMTP credentials, addresses or provider response bodies.
                log.warn(
                    "Order email {} will be retried: {}",
                    id,
                    ex.getClass().getSimpleName()
                );
            }
        }
    }
}
