package ru.aav.auth;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class PasswordRecovery {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final JavaMailSender mail;
    private final PasswordEncoder encoder;
    private final SessionRegistry sessions;
    private final String baseUrl, from, host;
    private final SecureRandom random = new SecureRandom();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.AbortPolicy());
    private final Map<String, Long> recent = new HashMap<>();
    private long window;
    private int requests;

    public PasswordRecovery(JdbcTemplate db, TransactionTemplate tx, JavaMailSender mail,
        PasswordEncoder encoder, SessionRegistry sessions,
        @Value("${bookstore.public-url:https://book-store-cgvx.onrender.com}") String baseUrl,
        @Value("${bookstore.review-mail.from:}") String from,
        @Value("${spring.mail.host:localhost}") String host) {
        this.db = db; this.tx = tx; this.mail = mail; this.encoder = encoder;
        this.sessions = sessions; this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.from = from; this.host = host;
    }

    public synchronized void request(String email) {
        if (from.isBlank() || host.isBlank() || host.equals("localhost"))
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Отправка писем пока недоступна");
        long now = System.currentTimeMillis();
        recent.entrySet().removeIf(e -> now - e.getValue() >= 60_000);
        if (now - window >= 60_000) { window = now; requests = 0; }
        String address = CurrentAccount.normalize(email);
        if (requests >= 30 || recent.containsKey(address)) return;
        requests++; recent.put(address, now);
        try { worker.execute(() -> {
            try { issue(address); }
            catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Password recovery delivery failed: {}", ex.getClass().getSimpleName());
            }
        }); } catch (RejectedExecutionException ignored) { }
    }

    void issue(String email) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(token);
        Boolean issued = tx.execute(status -> {
            var ids = db.queryForList("SELECT id FROM store_users WHERE email = ? FOR UPDATE", Long.class, email);
            if (ids.isEmpty()) return false;
            long id = ids.get(0);
            var times = db.queryForList("SELECT requested_at FROM password_resets WHERE user_id = ?", Timestamp.class, id);
            if (!times.isEmpty() && times.get(0).toInstant().isAfter(Instant.now().minusSeconds(60))) return false;
            db.update("DELETE FROM password_resets WHERE user_id = ?", id);
            db.update("INSERT INTO password_resets(user_id, token_hash, expires_at, requested_at) VALUES (?, ?, ?, ?)",
                id, hash, Timestamp.from(Instant.now().plusSeconds(1800)), Timestamp.from(Instant.now()));
            return true;
        });
        if (!Boolean.TRUE.equals(issued)) return;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject("Переплёт — восстановление пароля");
        message.setText("Для смены пароля откройте ссылку (действует 30 минут):\n\n" + baseUrl +
            "/#reset-password=" + token + "\n\nЕсли вы не запрашивали смену пароля, просто проигнорируйте письмо. Ваш пароль не изменится.");
        try { mail.send(message); }
        catch (RuntimeException ex) { db.update("DELETE FROM password_resets WHERE token_hash = ?", hash); throw ex; }
    }

    public void reset(String token, String password, String confirmation) {
        if (!password.equals(confirmation)) throw new ResponseStatusException(BAD_REQUEST, "Пароли не совпадают");
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(BAD_REQUEST, "Пароль должен содержать от 8 символов и не более 72 байт");
        if (!token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String digest = hash(token);
        String email = tx.execute(status -> {
            var ids = db.queryForList("SELECT user_id FROM password_resets WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP", Long.class, digest);
            if (ids.isEmpty()) throw invalid();
            long id = ids.get(0);
            String address = db.queryForObject("SELECT email FROM store_users WHERE id = ? FOR UPDATE", String.class, id);
            if (db.update("DELETE FROM password_resets WHERE user_id = ? AND token_hash = ? AND expires_at > CURRENT_TIMESTAMP", id, digest) != 1) throw invalid();
            db.update("UPDATE store_users SET password_hash = ? WHERE id = ?", encoder.encode(password), id);
            db.update("DELETE FROM persistent_logins WHERE username = ?", address);
            return address;
        });
        for (Object principal : sessions.getAllPrincipals())
            if (principal instanceof UserDetails u && u.getUsername().equals(email))
                sessions.getAllSessions(principal, false).forEach(s -> s.expireNow());
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(BAD_REQUEST, "Ссылка недействительна или истекла. Запросите новое письмо."); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @PreDestroy void close() { worker.shutdownNow(); }
}
