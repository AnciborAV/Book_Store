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
        return new Selection(favorites, cart);
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

    public void quantity(long user, long book, int quantity) {
        if (
            jdbc.update(
                "UPDATE cart_items SET quantity=? WHERE user_id=? AND book_id=?",
                quantity,
                user,
                book
            ) == 0
        ) {
            jdbc.update(
                "INSERT INTO cart_items (user_id,book_id,quantity) VALUES (?,?,?)",
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
