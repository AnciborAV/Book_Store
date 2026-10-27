package ru.aav.catalog;

import java.util.List;
import java.util.Map;

public record Selection(List<Long> favorites, Map<Long, Integer> cart) {}
