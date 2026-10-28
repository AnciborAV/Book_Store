package ru.aav.notifications;

import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewsController {
    private final JdbcTemplate jdbc;
    private final ReviewMailSender sender;
    private final ReviewMailQueue queue;
    public record BookReview(long id,long bookId,String book,String bookAuthor,String author,int rating,
        String body,LocalDateTime created,boolean unread) {}
    public record Page(List<BookReview> items,long total) {}
    @GetMapping
    public Page list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="false") boolean unread) {
        if(page<0||page>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String where=unread?" WHERE r.admin_read_at IS NULL":"";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM book_reviews r"+where,Long.class);
        var rows=jdbc.query("SELECT r.*,b.name,b.author,u.display_name FROM book_reviews r JOIN books b ON b.id=r.book_id JOIN store_users u ON u.id=r.user_id"
            +where+" ORDER BY r.created DESC,r.id DESC LIMIT 20 OFFSET ?",(r,n)->new BookReview(r.getLong("id"),r.getLong("book_id"),
            r.getString("name"),r.getString("author"),r.getString("display_name"),r.getInt("rating"),r.getString("body"),
            r.getTimestamp("created").toLocalDateTime(),r.getTimestamp("admin_read_at")==null),page*20);
        return new Page(rows,total);
    }
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable long id) {
        if(jdbc.update("UPDATE book_reviews SET admin_read_at=CURRENT_TIMESTAMP WHERE id=?",id)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    public record Status(long unreadBooks,long pendingSite,boolean mailConfigured,String mailMessage,long pendingMail,long failedMail,long sentMail) {}
    private long count(String sql) { return jdbc.queryForObject(sql,Long.class); }
    @GetMapping("/status")
    public Status status() {
        return new Status(count("SELECT COUNT(*) FROM book_reviews WHERE admin_read_at IS NULL"),
            count("SELECT COUNT(*) FROM site_feedback WHERE state='PENDING'"),sender.configured(),sender.configurationMessage(),
            count("SELECT COUNT(*) FROM review_mail WHERE sent_at IS NULL AND attempts<5"),
            count("SELECT COUNT(*) FROM review_mail WHERE sent_at IS NULL AND attempts>=5"),
            count("SELECT COUNT(*) FROM review_mail WHERE sent_at IS NOT NULL"));
    }
    @PostMapping("/retry-mail")
    public Map<String,Integer> retry() {
        if(!sender.configured()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Сначала настройте отправку почты на сервере.");
        return Map.of("queued",queue.retryFailed());
    }
}
