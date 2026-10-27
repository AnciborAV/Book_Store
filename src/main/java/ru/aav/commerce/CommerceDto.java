package ru.aav.commerce;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Immutable API snapshots. Monetary arithmetic uses BigDecimal, never double. */
public final class CommerceDto {

    private CommerceDto() {}

    public record Order(
        String id,
        long userId,
        String buyerName,
        String buyerEmail,
        OrderStatus status,
        BigDecimal total,
        String currency,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record Item(
        long bookId,
        String name,
        String author,
        int quantity,
        BigDecimal unitPrice
    ) {}

    public record Event(OrderStatus status, String actor, Instant createdAt) {}

    public record Detail(Order order, List<Item> items, List<Event> events) {}

    public record Stock(
        long bookId,
        String name,
        String author,
        String category,
        BigDecimal price,
        int stock,
        int reserved,
        int available
    ) {}

    public record Movement(
        long id,
        long bookId,
        String bookTitle,
        String actor,
        String kind,
        int stockDelta,
        int reservedDelta,
        int stockAfter,
        int reservedAfter,
        String orderId,
        String note,
        Instant createdAt
    ) {}
}
