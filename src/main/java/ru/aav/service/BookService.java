package ru.aav.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.aav.exception.EntityNotFoundException;
import ru.aav.model.Book;
import ru.aav.repository.BookRepository;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;

    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    public Book findById(Long id) {
        log.info("trying to find book with id {}", id);
        return bookRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
    }

    public Book create(Book book) {
        log.info("Book to create {}", book);
        return bookRepository.save(book);

    }
}
