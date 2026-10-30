package ru.aav.commerce;

import static ru.aav.commerce.CommerceDto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import ru.aav.auth.StoreUser;
import ru.aav.model.Book;

@Repository
@RequiredArgsConstructor
public class CommerceRepository {

    private final JdbcTemplate jdbc;
    private static final RowMapper<Order> ORDER = (r, n) ->
        new Order(
            r.getString("id"),
            r.getLong("user_id"),
            r.getString("buyer_name"),
            r.getString("buyer_email"),
            OrderStatus.valueOf(r.getString("status")),
            r.getBigDecimal("total"),
            r.getString("currency"),
            r.getTimestamp("created_at").toInstant(),
            r.getTimestamp("updated_at").toInstant(),
            formatOrderNumber(r.getLong("order_number"))
        );

    private static String formatOrderNumber(long number) {
        return String.format(java.util.Locale.ROOT, "%06d", number);
    }

    public Optional<Order> replay(long user, String key) {
        return jdbc
            .query(
                "SELECT * FROM store_orders WHERE user_id=? AND request_key=?",
                ORDER,
                user,
                key
            )
            .stream()
            .findFirst();
    }

    public Optional<Order> find(String id, boolean lock) {
        return jdbc
            .query(
                "SELECT * FROM store_orders WHERE id=?" +
                    (lock ? " FOR UPDATE" : ""),
                ORDER,
                id
            )
            .stream()
            .findFirst();
    }

    public List<Order> list(Long user, int page) {
        return user == null
            ? jdbc.query(
                  "SELECT * FROM store_orders ORDER BY created_at DESC,id LIMIT 50 OFFSET ?",
                  ORDER,
                  page * 50
              )
            : jdbc.query(
                  "SELECT * FROM store_orders WHERE user_id=? ORDER BY created_at DESC,id LIMIT 50 OFFSET ?",
                  ORDER,
                  user,
                  page * 50
              );
    }

    public List<Item> items(String id) {
        return jdbc.query(
            "SELECT * FROM order_items WHERE order_id=? ORDER BY book_id",
            (r, n) ->
                new Item(
                    r.getLong("book_id"),
                    r.getString("book_name"),
                    r.getString("book_author"),
                    r.getInt("quantity"),
                    r.getBigDecimal("unit_price")
                ),
            id
        );
    }

    public List<Event> events(String id) {
        return jdbc.query(
            "SELECT e.*,u.display_name FROM order_events e JOIN store_users u ON u.id=e.actor_id WHERE order_id=? ORDER BY e.id",
            (r, n) ->
                new Event(
                    OrderStatus.valueOf(r.getString("status")),
                    r.getString("display_name"),
                    r.getTimestamp("created_at").toInstant()
                ),
            id
        );
    }

    public void create(
        String id,
        StoreUser user,
        String key,
        BigDecimal total
    ) {
        jdbc.update(
            "INSERT INTO store_orders(id,user_id,request_key,buyer_name,buyer_email,status,total) VALUES(?,?,?,?,?,'CREATED',?)",
            id,
            user.getId(),
            key,
            user.getDisplayName(),
            user.getEmail(),
            total
        );
    }

    public void item(String order, Book book, int quantity) {
        jdbc.update(
            "INSERT INTO order_items(order_id,book_id,book_name,book_author,quantity,unit_price) VALUES(?,?,?,?,?,?)",
            order,
            book.getId(),
            book.getName(),
            book.getAuthor(),
            quantity,
            book.getPrice()
        );
    }

    public void status(String id, OrderStatus status, long actor) {
        jdbc.update(
            "UPDATE store_orders SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            status.name(),
            id
        );
        jdbc.update(
            "INSERT INTO order_events(order_id,actor_id,status) VALUES(?,?,?)",
            id,
            actor,
            status.name()
        );
    }

    public void movement(
        Book book,
        long actor,
        String kind,
        int stockDelta,
        int reservedDelta,
        String order,
        String key,
        String note
    ) {
        jdbc.update(
            "INSERT INTO stock_movements(book_id,actor_id,kind,stock_delta,reserved_delta,stock_after,reserved_after,order_id,request_key,note) VALUES(?,?,?,?,?,?,?,?,?,?)",
            book.getId(),
            actor,
            kind,
            stockDelta,
            reservedDelta,
            book.getStock(),
            book.getReserved(),
            order,
            key,
            note
        );
    }

    public boolean receiptExists(
        long actor,
        String key,
        long book,
        int quantity,
        String note
    ) {
        var rows = jdbc.queryForList(
            "SELECT book_id,stock_delta,note FROM stock_movements WHERE actor_id=? AND request_key=?",
            actor,
            key
        );
        if (rows.isEmpty()) return false;
        var row = rows.getFirst();
        if (
            ((Number) row.get("book_id")).longValue() != book ||
            ((Number) row.get("stock_delta")).intValue() != quantity ||
            !note.equals(row.get("note"))
        ) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT,
            "Ключ поступления уже использован для других данных"
        );
        return true;
    }

    public List<Stock> stock() {
        return jdbc.query("SELECT * FROM books WHERE archived=FALSE ORDER BY id", (r, n) ->
            new Stock(
                r.getLong("id"),
                r.getString("name"),
                r.getString("author"),
                r.getString("category"),
                r.getBigDecimal("price"),
                r.getInt("stock"),
                r.getInt("reserved"),
                r.getInt("stock") - r.getInt("reserved")
            )
        );
    }

    public List<Movement> movements(int page) {
        return jdbc.query(
            "SELECT m.*,COALESCE(b.retired_title,b.name) AS name,u.display_name,o.order_number FROM stock_movements m JOIN books b ON b.id=m.book_id JOIN store_users u ON u.id=m.actor_id LEFT JOIN store_orders o ON o.id=m.order_id ORDER BY m.id DESC LIMIT 50 OFFSET ?",
            (r, n) ->
                new Movement(
                    r.getLong("id"),
                    r.getLong("book_id"),
                    r.getString("name"),
                    r.getString("display_name"),
                    r.getString("kind"),
                    r.getInt("stock_delta"),
                    r.getInt("reserved_delta"),
                    r.getInt("stock_after"),
                    r.getInt("reserved_after"),
                    r.getString("order_id"),
                    r.getString("note"),
                    r.getTimestamp("created_at").toInstant(),
                    r.getString("order_id") == null
                        ? null
                        : formatOrderNumber(r.getLong("order_number"))
                ),
            page * 50
        );
    }
}
