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

    private Long id;

    private String name;
    private String author;
}
