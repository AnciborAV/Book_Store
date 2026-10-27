package ru.aav.commerce;

import static ru.aav.commerce.CommerceDto.*;

import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.auth.CurrentAccount;
import ru.aav.catalog.SelectionRepository;
import ru.aav.model.Book;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final CommerceRepository repository;
    private final CurrentAccount accounts;
    private final SelectionRepository selection;
    private final InventoryService inventory;

    public List<Order> list(String email, int page) {
        return repository.list(accounts.require(email).getId(), page);
    }

    public List<Order> all(String email, int page) {
        inventory.admin(email);
        return repository.list(null, page);
    }

    public Detail detail(String email, String id) {
        var user = accounts.require(email);
        var order = repository
            .find(id, false)
            .orElseThrow(OrderService::missing);
        if (
            order.userId() != user.getId() && !"ADMIN".equals(user.getRole())
        ) throw missing();
        return detail(order);
    }

    private Detail detail(Order order) {
        return new Detail(
            order,
            repository.items(order.id()),
            repository.events(order.id())
        );
    }

    @Transactional
    public Detail checkout(String email, UUID key) {
        // Cart mutations acquire the same account lock. A retry with this key returns the original order.
        var user = accounts.lock(email);
        var replay = repository.replay(user.getId(), key.toString());
        if (replay.isPresent()) return detail(replay.get());
        var cart = selection.find(user.getId()).cart();
        if (cart.isEmpty()) throw conflict("Корзина пуста");
        var locked = new LinkedHashMap<Book, Integer>();
        BigDecimal total = BigDecimal.ZERO;
        // Always lock books in ascending ID order across checkout, shipment and cancellation.
        for (long id : new TreeSet<>(cart.keySet())) {
            var book = inventory.lock(id);
            int quantity = cart.get(id);
            if (book.getPrice() == null) throw conflict(
                "Не указана цена: " + book.getName()
            );
            if (book.getStock() - book.getReserved() < quantity) throw conflict(
                "Недостаточно книг на складе: " + book.getName()
            );
            locked.put(book, quantity);
            total = total.add(
                book.getPrice().multiply(BigDecimal.valueOf(quantity))
            );
        }
        String id = UUID.randomUUID().toString();
        repository.create(id, user, key.toString(), total);
        for (var entry : locked.entrySet()) {
            var book = entry.getKey();
            int quantity = entry.getValue();
            repository.item(id, book, quantity);
            book.setReserved(book.getReserved() + quantity);
            repository.movement(
                book,
                user.getId(),
                "RESERVE",
                0,
                quantity,
                id,
                null,
                "Резерв заказа"
            );
        }
        repository.status(id, OrderStatus.CREATED, user.getId());
        selection.clear(user.getId());
        return detail(repository.find(id, false).orElseThrow());
    }

    @Transactional
    public Detail transition(
        String email,
        String id,
        OrderStatus next,
        boolean adminRoute
    ) {
        var actor = adminRoute
            ? inventory.admin(email)
            : accounts.require(email);
        var order = repository
            .find(id, true)
            .orElseThrow(OrderService::missing);
        if (
            !adminRoute &&
            (order.userId() != actor.getId() || next != OrderStatus.CANCELLED)
        ) throw missing();
        if (order.status() == next) return detail(order);
        if (!order.status().canMoveTo(next)) throw conflict(
            "Недопустимый переход статуса заказа"
        );
        if (next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED) {
            for (var item : repository.items(id)) {
                var book = inventory.lock(item.bookId());
                if (book.getReserved() < item.quantity()) throw conflict(
                    "Нарушен складской резерв"
                );
                book.setReserved(book.getReserved() - item.quantity());
                int delta = next == OrderStatus.SHIPPED ? -item.quantity() : 0;
                book.setStock(book.getStock() + delta);
                repository.movement(
                    book,
                    actor.getId(),
                    next == OrderStatus.SHIPPED ? "SHIP" : "RELEASE",
                    delta,
                    -item.quantity(),
                    id,
                    null,
                    next == OrderStatus.SHIPPED
                        ? "Отправка заказа"
                        : "Отмена заказа"
                );
            }
        }
        repository.status(id, next, actor.getId());
        return detail(repository.find(id, false).orElseThrow());
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Заказ не найден"
        );
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
