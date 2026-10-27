package ru.aav.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
@Table(name = "books")
public class Book extends BasicEntity {

    private String name;
    private String author;
    private String category;

    @jakarta.persistence.Column(precision = 10, scale = 2)
    private java.math.BigDecimal price;

    private int stock;
    private int reserved;
}
