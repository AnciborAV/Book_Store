package ru.aav.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBookRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String author) {
}
