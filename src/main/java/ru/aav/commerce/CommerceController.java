package ru.aav.commerce;

import static ru.aav.commerce.CommerceDto.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api")
public class CommerceController {

    private final OrderService orders;
    private final InventoryService inventory;
    private final OrderMailQueue mail;

    public record Checkout(@NotNull UUID requestKey) {}

    public record Transition(@NotNull OrderStatus status) {}

    public record Receipt(
        @Min(1) @Max(100000) int quantity,
        @NotNull UUID requestKey,
        @NotBlank @Size(max = 500) String note
    ) {}

    public record EditBook(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String author,
        @Size(max = 255) String category,
        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 8, fraction = 2)
        BigDecimal price
    ) {}

    @GetMapping("/orders")
    public List<Order> mine(
        Principal user,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page
    ) {
        return orders.list(user.getName(), page);
    }

    @PostMapping("/orders")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Detail checkout(Principal user, @Valid @RequestBody Checkout body) {
        return orders.checkout(user.getName(), body.requestKey());
    }

    @GetMapping("/orders/{id}")
    public Detail detail(Principal user, @PathVariable UUID id) {
        return orders.detail(user.getName(), id.toString());
    }

    @GetMapping("/orders/{id}/email")
    public OrderMailQueue.Status emailStatus(
        Principal user,
        @PathVariable UUID id
    ) {
        orders.detail(user.getName(), id.toString());
        return mail.status(id.toString());
    }

    @PostMapping("/orders/{id}/cancel")
    public Detail cancel(Principal user, @PathVariable UUID id) {
        return orders.transition(
            user.getName(),
            id.toString(),
            OrderStatus.CANCELLED,
            false
        );
    }

    @GetMapping("/admin/orders")
    public List<Order> all(
        Principal user,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page
    ) {
        return orders.all(user.getName(), page);
    }

    @PutMapping("/admin/orders/{id}/status")
    public Detail status(
        Principal user,
        @PathVariable UUID id,
        @Valid @RequestBody Transition body
    ) {
        return orders.transition(
            user.getName(),
            id.toString(),
            body.status(),
            true
        );
    }

    @GetMapping("/admin/inventory")
    public List<Stock> stock(Principal user) {
        return inventory.list(user.getName());
    }

    @PutMapping("/admin/books/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void edit(
        Principal user,
        @PathVariable long id,
        @Valid @RequestBody EditBook body
    ) {
        inventory.update(
            user.getName(),
            id,
            body.name(),
            body.author(),
            body.category(),
            body.price()
        );
    }

    @PostMapping("/admin/books/{id}/receipts")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void receive(
        Principal user,
        @PathVariable long id,
        @Valid @RequestBody Receipt body
    ) {
        inventory.receive(
            user.getName(),
            id,
            body.quantity(),
            body.requestKey(),
            body.note()
        );
    }

    @GetMapping("/admin/stock-movements")
    public List<Movement> movements(
        Principal user,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page
    ) {
        return inventory.movements(user.getName(), page);
    }
}
