package ru.aav.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.lang.NonNull;
import ru.aav.model.Book;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends CrudRepository<Book, Long> {

    @NonNull
    @Override
    List<Book> findAll();

    @NonNull
    @Override
    Optional<Book> findById(@NonNull Long id);

    @NonNull
    @Override
    <S extends Book> S save(@NonNull S entity);
}
