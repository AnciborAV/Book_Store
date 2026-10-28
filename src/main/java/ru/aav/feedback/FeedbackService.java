package ru.aav.feedback;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.auth.CurrentAccount;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackService {
    private final JdbcTemplate jdbc;
    private final CurrentAccount accounts;
    private final ru.aav.notifications.ReviewMailQueue mail;
    public enum Kind { REVIEW, ISSUE, IDEA }
    public enum State { PENDING, PUBLISHED, ARCHIVED }
    public record Entry(String id, String author, Kind kind, String body, boolean publicConsent,
                        State state, Instant createdAt, int version) {}
    public record Page(List<Entry> items, long total) {}

    public Page list(int page, boolean admin, State state) {
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String where = admin ? " WHERE state=?" : " WHERE state='PUBLISHED' AND public_consent=TRUE";
        Object[] args = admin ? new Object[]{state.name()} : new Object[]{};
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM site_feedback" + where, Long.class, args);
        var params = new java.util.ArrayList<Object>(java.util.Arrays.asList(args));
        params.add(20); params.add(page * 20);
        var rows = jdbc.query("SELECT * FROM site_feedback" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
            (r,n) -> new Entry(r.getString("id"), r.getString("author"), Kind.valueOf(r.getString("kind")),
                r.getString("body"), r.getBoolean("public_consent"), State.valueOf(r.getString("state")),
                r.getTimestamp("created_at").toInstant(), r.getInt("version")), params.toArray());
        return new Page(rows, count);
    }

    @Transactional
    public String create(String email, UUID requestId, Kind kind, String body, boolean consent) {
        var user = accounts.lock(email);
        // A retry after a lost response must not create a duplicate message.
        var previous = jdbc.queryForList("SELECT id FROM site_feedback WHERE user_id=? AND request_id=?",
            String.class, user.getId(), requestId.toString());
        if (!previous.isEmpty()) return previous.getFirst();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM site_feedback WHERE user_id=? AND created_at>?",
            Integer.class, user.getId(), Timestamp.from(Instant.now().minusSeconds(60))) > 0)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Сообщение уже отправлено. Следующее можно отправить через минуту.");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO site_feedback(id,user_id,request_id,author,kind,body,public_consent) VALUES(?,?,?,?,?,?,?)",
            id, user.getId(), requestId.toString(), user.getDisplayName(), kind.name(), body.strip(), consent);
        String label = switch(kind) {case REVIEW -> "Отзыв о сайте"; case ISSUE -> "Замечание"; case IDEA -> "Предложение";};
        mail.enqueue("SITE:"+id,"Новое сообщение о сайте",
            label+"\nАвтор: "+user.getDisplayName()+"\n\n"+body.strip()
            +"\n\n"+(consent ? "Автор разрешил публикацию после проверки." : "Сообщение только для администратора. Не публиковать.")
            +"\nОткройте кабинет администратора → Отзывы и уведомления → О сайте.");
        return id;
    }

    @Transactional
    public void moderate(UUID id, int version, State state) {
        int changed = jdbc.update("UPDATE site_feedback SET state=?,version=version+1 WHERE id=? AND version=? AND (public_consent=TRUE OR ?<>'PUBLISHED')",
            state.name(), id.toString(), version, state.name());
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Обновите список. Сообщение изменено или автор не разрешил публикацию.");
    }
}
