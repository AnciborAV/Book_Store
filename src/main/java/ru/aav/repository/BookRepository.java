package ru.aav.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.aav.model.Book;

/** Typed persistence operations, including stable sorting and explicit flush. */
public interface BookRepository extends JpaRepository<Book, Long> {}
