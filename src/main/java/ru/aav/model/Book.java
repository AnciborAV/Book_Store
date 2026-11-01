package ru.aav.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private String retiredTitle;
    private boolean archived;
    private long managementRevision;

    private int stock;
    private int reserved;
}
