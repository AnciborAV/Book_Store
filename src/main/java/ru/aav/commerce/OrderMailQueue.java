package ru.aav.commerce;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable outbox: an order and its notification are committed together. */
@Service
public class OrderMailQueue {

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public OrderMailQueue(
        JdbcTemplate jdbc,
        @Value("${bookstore.mail.enabled:false}") boolean enabled
    ) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.MANDATORY
    )
    public void enqueue(String orderId) {
        jdbc.update("INSERT INTO order_mail(order_id) VALUES (?)", orderId);
    }

    public List<String> due() {
        return jdbc.queryForList(
            "SELECT order_id FROM order_mail WHERE sent_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP ORDER BY next_attempt_at LIMIT 10",
            String.class
        );
    }

    /** Atomic lease prevents two workers from sending the same pending row concurrently. */
    public boolean claim(String id) {
        return (
            jdbc.update(
                "UPDATE order_mail SET attempts=attempts+1, next_attempt_at=? WHERE order_id=? AND sent_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP",
                Timestamp.from(Instant.now().plusSeconds(600)),
                id
            ) == 1
        );
    }

    public void sent(String id) {
        jdbc.update(
            "UPDATE order_mail SET sent_at=CURRENT_TIMESTAMP WHERE order_id=?",
            id
        );
    }

    public record Status(String state, String message) {}

    public Status status(String id) {
        var rows = jdbc.query(
            "SELECT attempts, sent_at FROM order_mail WHERE order_id=?",
            (rs, n) -> {
                if (rs.getTimestamp("sent_at") != null) return new Status(
                    "SENT",
                    "Подтверждение заказа передано почтовому серверу. Проверьте входящие и папку «Спам»."
                );
                if (!enabled) return new Status(
                    "DISABLED",
                    "Заказ сохранён. Письмо ожидает настройки почты магазина."
                );
                if (rs.getInt("attempts") > 0) return new Status(
                    "PENDING",
                    "Письмо отправляется или ожидает повторной попытки. Заказ сохранён."
                );
                return new Status(
                    "PENDING",
                    "Подтверждение заказа поставлено в очередь отправки на почту покупателя."
                );
            },
            id
        );
        return rows.isEmpty()
            ? new Status(
                  "NOT_QUEUED",
                  "Для этого старого заказа письмо не создавалось."
              )
            : rows.getFirst();
    }
}
