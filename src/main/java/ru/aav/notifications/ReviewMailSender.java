package ru.aav.notifications;

import jakarta.mail.internet.InternetAddress;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

/** Sends committed review notifications outside the review transaction. */
@Component
@EnableScheduling
public class ReviewMailSender {
    private final ReviewMailQueue queue;
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from, recipients, host;
    public ReviewMailSender(ReviewMailQueue queue, JavaMailSender sender,
        @Value("${bookstore.review-mail.enabled:false}") boolean enabled,
        @Value("${bookstore.review-mail.from:}") String from,
        @Value("${bookstore.review-mail.recipients:}") String recipients,
        @Value("${spring.mail.host:}") String host) {
        this.queue=queue;this.sender=sender;this.enabled=enabled;
        this.from=from;this.recipients=recipients;this.host=host;
    }
    private boolean address(String value) {
        try { if(value.isBlank()||value.contains("\r")||value.contains("\n"))return false;
            new InternetAddress(value,true).validate();return true;
        } catch(Exception e) {return false;}
    }
    private String[] addresses() {
        return Arrays.stream(recipients.split(",",-1)).map(String::trim).distinct().toArray(String[]::new);
    }
    public boolean configured() {
        return enabled&&!host.isBlank()&&!host.equals("localhost")&&address(from)
            &&Arrays.stream(addresses()).allMatch(this::address);
    }
    public String configurationMessage() {
        if(!enabled) return "Email-уведомления выключены. Отзывы сохраняются, письма ожидают подключения отправки.";
        if(!configured()) return "Для отправки нужны SMTP-сервер, адрес отправителя и адреса получателей. Проверьте настройки сервера.";
        return "SMTP-отправка включена. Письма передаются почтовому серверу; окончательная доставка зависит от него. Проверьте папку «Спам».";
    }
    @Scheduled(fixedDelayString="${bookstore.review-mail.poll-ms:15000}", initialDelayString="${bookstore.review-mail.initial-delay-ms:15000}")
    public void sendPending() {
        if(!configured()) return;
        for(var mail:queue.due()) {
            if(!queue.claim(mail.id()))continue;
            try {
                SimpleMailMessage message=new SimpleMailMessage();
                message.setFrom(from);message.setTo(addresses());
                message.setSubject(mail.subject());message.setText(mail.body());
                sender.send(message);
                queue.sent(mail.id());
            } catch(Exception e) {
                queue.failed(mail.id(),"Ошибка SMTP-отправки; проверьте настройки почты");
                LoggerFactory.getLogger(getClass()).warn("Review email {} deferred ({})",mail.id(),e.getClass().getSimpleName());
            }
        }
    }
}
