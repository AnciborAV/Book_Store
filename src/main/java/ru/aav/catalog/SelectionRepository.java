package ru.aav.catalog;

import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SelectionRepository {

    private final JdbcTemplate jdbc;

    public Selection find(long user) {
        var favorites = jdbc.queryForList(
            "SELECT book_id FROM user_favorites WHERE user_id=? ORDER BY book_id",
            Long.class,
            user
        );
        var cart = new LinkedHashMap<Long, Integer>();
        jdbc.query(
            "SELECT book_id, quantity FROM cart_items WHERE user_id=? ORDER BY book_id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                cart.put(rs.getLong("book_id"), rs.getInt("quantity")),
            user
        );
        return new Selection(favorites, cart, jdbc.queryForList("SELECT book_id FROM library_favorites WHERE user_id=? ORDER BY book_id", String.class, user));
    }

    public void favorite(long user, long book) {
        jdbc.update(
            "INSERT INTO user_favorites (user_id,book_id) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM user_favorites WHERE user_id=? AND book_id=?)",
            user,
            book,
            user,
            book
        );
    }

    public void unfavorite(long user, long book) {
        jdbc.update(
            "DELETE FROM user_favorites WHERE user_id=? AND book_id=?",
            user,
            book
        );
    }

    public void libraryFavorite(long user, String book) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM java_library WHERE id=?", Integer.class, book) == 0)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Книга не найдена");
        jdbc.update("INSERT INTO library_favorites(user_id,book_id) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM library_favorites WHERE user_id=? AND book_id=?)", user, book, user, book);
    }
    public void libraryUnfavorite(long user, String book) {
        jdbc.update("DELETE FROM library_favorites WHERE user_id=? AND book_id=?", user, book);
    }

    public void quantity(long user, long book, int quantity) {
        if (
            jdbc.update(
                "UPDATE cart_items SET quantity=?, updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND book_id=?",
                quantity,
                user,
                book
            ) == 0
        ) {
            jdbc.update(
                "INSERT INTO cart_items (user_id,book_id,quantity,added_at,updated_at) VALUES (?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                user,
                book,
                quantity
            );
        }
    }

    public void remove(long user, long book) {
        jdbc.update(
            "DELETE FROM cart_items WHERE user_id=? AND book_id=?",
            user,
            book
        );
    }

    public void clear(long user) {
        jdbc.update("DELETE FROM cart_items WHERE user_id=?", user);
    }
}
