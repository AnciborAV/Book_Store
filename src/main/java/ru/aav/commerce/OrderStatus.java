package ru.aav.commerce;

public enum OrderStatus {
    CREATED,
    CONFIRMED,
    PACKED,
    SHIPPED,
    COMPLETED,
    CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case CREATED -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == PACKED || next == CANCELLED;
            case PACKED -> next == SHIPPED || next == CANCELLED;
            case SHIPPED -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
