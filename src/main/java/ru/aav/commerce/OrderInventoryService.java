package ru.aav.commerce;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.model.Book;

/** Order stock operations run inside the transaction owned by OrderService. */
@Service
@RequiredArgsConstructor
public class OrderInventoryService {
    private final InventoryService inventory;
    private final CommerceRepository repository;

    record Line(Book book, int quantity) {}
    record PreparedCart(List<Line> lines, BigDecimal total) {}

    PreparedCart prepare(Map<Long, Integer> cart) {
        if (cart.isEmpty()) throw conflict("Корзина пуста");
        var lines = new ArrayList<Line>();
        BigDecimal total = BigDecimal.ZERO;
        // The ascending lock order is shared by checkout, shipment and cancellation.
        for (long id : new TreeSet<>(cart.keySet())) {
            var book = inventory.lock(id);
            if (book.isArchived()) {
                throw conflict("Книга удалена из каталога: " + book.getName());
            }
            int quantity = cart.get(id);
            validateAvailability(book, quantity);
            lines.add(new Line(book, quantity));
            total = total.add(book.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }
        return new PreparedCart(List.copyOf(lines), total);
    }

    private void validateAvailability(Book book, int quantity) {
        if (book.getPrice() == null) {
            throw conflict("Не указана цена: " + book.getName());
        }
        if (book.getStock() - book.getReserved() < quantity) {
            throw conflict("Недостаточно книг на складе: " + book.getName());
        }
    }

    void reserve(String orderId, long actorId, PreparedCart cart) {
        for (var line : cart.lines()) {
            var book = line.book();
            int quantity = line.quantity();
            repository.item(orderId, book, quantity);
            book.setReserved(book.getReserved() + quantity);
            repository.movement(book, actorId, "RESERVE", 0, quantity,
                orderId, null, "Резерв заказа");
        }
    }

    void applyTransition(String orderId, long actorId, OrderStatus next) {
        if (next != OrderStatus.SHIPPED && next != OrderStatus.CANCELLED) return;
        boolean shipping = next == OrderStatus.SHIPPED;
        // CommerceRepository.items returns rows in ascending book_id order.
        for (var item : repository.items(orderId)) {
            var book = inventory.lock(item.bookId());
            if (book.getReserved() < item.quantity()) {
                throw conflict("Нарушен складской резерв");
            }
            book.setReserved(book.getReserved() - item.quantity());
            int delta = shipping ? -item.quantity() : 0;
            book.setStock(book.getStock() + delta);
            repository.movement(book, actorId, shipping ? "SHIP" : "RELEASE",
                delta, -item.quantity(), orderId, null,
                shipping ? "Отправка заказа" : "Отмена заказа");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
