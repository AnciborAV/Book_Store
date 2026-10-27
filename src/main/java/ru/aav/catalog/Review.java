package ru.aav.catalog;
import java.time.LocalDateTime;
public record Review(long id, String author, int rating, String body, LocalDateTime created, boolean own) {}
