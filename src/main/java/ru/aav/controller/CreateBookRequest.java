package ru.aav.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBookRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String author,
        @Size(max = 255) String category) {
    public CreateBookRequest(String name, String author) { this(name, author, null); }
}
