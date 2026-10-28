package ru.aav.notifications;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class ReviewMailQueue {
    private final JdbcTemplate jdbc;
    public record Mail(String id,String subject,String body) {}
    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(String source,String subject,String body) {
        jdbc.update("INSERT INTO review_mail(id,source_key,subject,body) VALUES(?,?,?,?)",
            UUID.randomUUID().toString(),source,subject,body);
    }
    public List<Mail> due() {
        return jdbc.query("SELECT id,subject,body FROM review_mail WHERE sent_at IS NULL AND attempts<5 AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT 5",
            (r,n)->new Mail(r.getString(1),r.getString(2),r.getString(3)));
    }
    public boolean claim(String id) {
        return jdbc.update("UPDATE review_mail SET attempts=attempts+1,next_attempt_at=? WHERE id=? AND sent_at IS NULL AND attempts<5 AND next_attempt_at<=CURRENT_TIMESTAMP",
            Timestamp.from(Instant.now().plusSeconds(600)),id)==1;
    }
    public void sent(String id) {
        jdbc.update("UPDATE review_mail SET sent_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=?",id);
    }
    public void failed(String id,String safeError) {
        jdbc.update("UPDATE review_mail SET last_error=? WHERE id=?",safeError,id);
    }
    public int retryFailed() {
        return jdbc.update("UPDATE review_mail SET attempts=0,last_error=NULL,next_attempt_at=CURRENT_TIMESTAMP WHERE sent_at IS NULL AND attempts>=5 AND next_attempt_at<=CURRENT_TIMESTAMP");
    }
}
