package ru.aav.commerce;

import static ru.aav.commerce.CommerceDto.Detail;
import static ru.aav.commerce.CommerceDto.Order;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.auth.CurrentAccount;
import ru.aav.catalog.SelectionRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final CommerceRepository repository;
    private final CurrentAccount accounts;
    private final SelectionRepository selection;
    private final InventoryService inventory;
    private final OrderMailQueue mail;
    private final OrderInventoryService orderInventory;

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
        ) {
            throw missing();
        }
        return detail(order);
    }

    @Transactional
    public Detail checkout(String email, UUID key) {
        // Cart mutations acquire the same account lock. A retry with this key returns the original order.
        var user = accounts.lock(email);
        var replay = repository.replay(user.getId(), key.toString());
        if (replay.isPresent()) {
            return detail(replay.get());
        }
        var cart = orderInventory.prepare(selection.find(user.getId()).cart());
        String id = UUID.randomUUID().toString();
        repository.create(id, user, key.toString(), cart.total());
        orderInventory.reserve(id, user.getId(), cart);
        repository.status(id, OrderStatus.CREATED, user.getId());
        selection.clear(user.getId());
        mail.enqueue(id);
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
        ) {
            throw missing();
        }
        if (order.status() == next) {
            return detail(order);
        }
        if (!order.status().canMoveTo(next)) {
            throw conflict("Недопустимый переход статуса заказа");
        }
        orderInventory.applyTransition(id, actor.getId(), next);
        repository.status(id, next, actor.getId());
        return detail(repository.find(id, false).orElseThrow());
    }

    private Detail detail(Order order) {
        return new Detail(
            order,
            repository.items(order.id()),
            repository.events(order.id())
        );
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
