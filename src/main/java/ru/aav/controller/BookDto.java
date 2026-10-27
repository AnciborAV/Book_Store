package ru.aav.controller;

import java.math.BigDecimal;

public record BookDto(
    Long id,
    String name,
    String author,
    String category,
    BigDecimal price,
    int availableStock
) {}
