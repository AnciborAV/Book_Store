package ru.aav.exception;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(Long id) {
        super("Book not found: " + id);
    }
}
