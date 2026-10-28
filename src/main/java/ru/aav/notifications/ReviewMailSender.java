package ru.aav.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

/** HTTPS works without SMTP ports. Never runs inside the transaction saving a review. */
@Component
@EnableScheduling
public class ReviewMailSender {
    private final ReviewMailQueue queue;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String key,from,to;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public ReviewMailSender(ReviewMailQueue queue,ObjectMapper json,
        @Value("${bookstore.review-mail.enabled:false}") boolean enabled,
        @Value("${bookstore.review-mail.api-key:}") String key,
        @Value("${bookstore.review-mail.from:}") String from,
        @Value("${bookstore.admin.email:}") String to) {
        this.queue=queue;this.json=json;this.enabled=enabled;this.key=key;this.from=from;this.to=to;
    }
    private boolean address(String value) {
        try { if(value.isBlank()||value.contains("\r")||value.contains("\n"))return false;
            new InternetAddress(value,true).validate();return true;
        } catch(Exception e) {return false;}
    }
    public boolean configured() { return enabled&&!key.isBlank()&&address(from)&&address(to); }
    public String configurationMessage() {
        if(!enabled) return "Email-уведомления выключены. Отзывы сохраняются, письма ожидают подключения отправки.";
        if(!configured()) return "Для отправки нужны ключ Resend, адрес отправителя и почта администратора. Проверьте настройки сервера.";
        return "Отправка включена. Письма передаются почтовому сервису; окончательная доставка зависит от него. Проверьте папку «Спам».";
    }
    @Scheduled(fixedDelayString="${bookstore.review-mail.poll-ms:15000}", initialDelayString="${bookstore.review-mail.initial-delay-ms:15000}")
    public void sendPending() {
        if(!configured()) return;
        for(var mail:queue.due()) {
            if(!queue.claim(mail.id()))continue;
            try {
                var payload=json.writeValueAsString(Map.of("from",from,"to",List.of(to),"subject",mail.subject(),"text",mail.body()));
                var req=HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+key)
                    .header("Content-Type","application/json").header("Idempotency-Key","review-"+mail.id())
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                var response=http.send(req,HttpResponse.BodyHandlers.discarding());
                if(response.statusCode()>=200&&response.statusCode()<300) queue.sent(mail.id());
                else queue.failed(mail.id(),"HTTP "+response.statusCode());
            } catch(InterruptedException e) { Thread.currentThread().interrupt();return;
            } catch(Exception e) {
                queue.failed(mail.id(),"Ошибка соединения с почтовым сервисом");
                LoggerFactory.getLogger(getClass()).warn("Review email {} deferred ({})",mail.id(),e.getClass().getSimpleName());
            }
        }
    }
}
