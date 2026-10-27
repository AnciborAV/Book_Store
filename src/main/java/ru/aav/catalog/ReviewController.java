package ru.aav.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.auth.UserRepository;
import ru.aav.repository.BookRepository;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final BookRepository books;

    public record Review(long id, String author, int rating, String body, LocalDateTime created, boolean own) {}
    public record ReviewRequest(@Min(1) @Max(5) int rating, @NotBlank @Size(max=2000) String body) {}

    private void requireBook(long id) {
        if (!books.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Книга не найдена");
    }
    private long userId(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }
    @GetMapping
    public List<Review> list(@PathVariable long bookId, Authentication auth) {
        requireBook(bookId);
        Long current = auth == null ? null : userId(auth);
        return jdbc.query("SELECT r.*, u.display_name FROM book_reviews r JOIN store_users u ON u.id=r.user_id WHERE r.book_id=? ORDER BY r.created DESC, r.id DESC",
            (rs, row) -> new Review(rs.getLong("id"), rs.getString("display_name"), rs.getInt("rating"), rs.getString("body"),
                rs.getTimestamp("created").toLocalDateTime(), current != null && current == rs.getLong("user_id")), bookId);
    }
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void save(@PathVariable long bookId, Authentication auth, @Valid @RequestBody ReviewRequest body) {
        requireBook(bookId);
        long user = userId(auth);
        int updated = jdbc.update("UPDATE book_reviews SET rating=?, body=? WHERE book_id=? AND user_id=?", body.rating(), body.body().strip(), bookId, user);
        if (updated == 0) jdbc.update("INSERT INTO book_reviews (book_id,user_id,rating,body) VALUES (?,?,?,?)", bookId, user, body.rating(), body.body().strip());
    }
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long bookId, @PathVariable long reviewId, Authentication auth) {
        if (jdbc.update("DELETE FROM book_reviews WHERE id=? AND book_id=? AND user_id=?", reviewId, bookId, userId(auth)) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Отзыв не найден");
    }
}
