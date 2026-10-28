package ru.aav.catalog;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReviewRepository {

    private final JdbcTemplate jdbc;

    public List<Review> find(long book, Long user) {
        return jdbc.query(
            "SELECT r.*, u.display_name FROM book_reviews r JOIN store_users u ON u.id=r.user_id WHERE r.book_id=? ORDER BY r.created DESC, r.id DESC",
            (rs, row) ->
                new Review(
                    rs.getLong("id"),
                    rs.getString("display_name"),
                    rs.getInt("rating"),
                    rs.getString("body"),
                    rs.getTimestamp("created").toLocalDateTime(),
                    user != null && user == rs.getLong("user_id")
                ),
            book
        );
    }

    public boolean save(long book, long user, int rating, String body) {
        if (
            jdbc.update(
                "UPDATE book_reviews SET rating=?, body=?, admin_read_at=NULL WHERE book_id=? AND user_id=?",
                rating,
                body,
                book,
                user
            ) == 0
        ) {
            jdbc.update(
                "INSERT INTO book_reviews (book_id,user_id,rating,body) VALUES (?,?,?,?)",
                book,
                user,
                rating,
                body
            );
            return true;
        }
        return false;
    }

    public long id(long book,long user) {
        return jdbc.queryForObject("SELECT id FROM book_reviews WHERE book_id=? AND user_id=?",Long.class,book,user);
    }

    public boolean delete(long book, long review, long user) {
        return (
            jdbc.update(
                "DELETE FROM book_reviews WHERE id=? AND book_id=? AND user_id=?",
                review,
                book,
                user
            ) > 0
        );
    }
}
