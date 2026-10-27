package ru.aav.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.auth.UserRepository;
import ru.aav.repository.BookRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/selection")
@RequiredArgsConstructor
public class SelectionController {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final BookRepository books;

    public record Selection(List<Long> favorites, Map<Long, Integer> cart) {}
    public record Quantity(@Min(1) @Max(99) int quantity) {}

    private long userId(Authentication authentication) {
        if (authentication == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }
    private void requireBook(long id) {
        if (!books.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Книга не найдена");
    }
    @GetMapping
    public Selection get(Authentication authentication) {
        long user = userId(authentication);
        List<Long> favorites = jdbc.queryForList("SELECT book_id FROM user_favorites WHERE user_id=? ORDER BY book_id", Long.class, user);
        Map<Long, Integer> cart = new LinkedHashMap<>();
        jdbc.query("SELECT book_id, quantity FROM cart_items WHERE user_id=? ORDER BY book_id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> cart.put(rs.getLong("book_id"), rs.getInt("quantity")), user);
        return new Selection(favorites, cart);
    }
    @PutMapping("/favorites/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(Authentication authentication, @PathVariable long bookId) {
        long user = userId(authentication);
        requireBook(bookId);
        try {
            jdbc.update("INSERT INTO user_favorites (user_id,book_id) VALUES (?,?)", user, bookId);
        } catch (DuplicateKeyException alreadySaved) {
            // Repeating PUT keeps the same single favorite.
        }
    }
    @DeleteMapping("/favorites/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(Authentication authentication, @PathVariable long bookId) {
        jdbc.update("DELETE FROM user_favorites WHERE user_id=? AND book_id=?", userId(authentication), bookId);
    }
    @PutMapping("/cart/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quantity(Authentication authentication, @PathVariable long bookId, @Valid @RequestBody Quantity body) {
        long user = userId(authentication);
        requireBook(bookId);
        int updated = jdbc.update("UPDATE cart_items SET quantity=? WHERE user_id=? AND book_id=?", body.quantity(), user, bookId);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO cart_items (user_id,book_id,quantity) VALUES (?,?,?)", user, bookId, body.quantity());
            } catch (DuplicateKeyException concurrentInsert) {
                jdbc.update("UPDATE cart_items SET quantity=? WHERE user_id=? AND book_id=?", body.quantity(), user, bookId);
            }
        }
    }
    @DeleteMapping("/cart/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication, @PathVariable long bookId) {
        jdbc.update("DELETE FROM cart_items WHERE user_id=? AND book_id=?", userId(authentication), bookId);
    }
    @DeleteMapping("/cart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication authentication) {
        jdbc.update("DELETE FROM cart_items WHERE user_id=?", userId(authentication));
    }
}
