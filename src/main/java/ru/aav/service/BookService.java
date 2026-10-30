package ru.aav.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aav.exception.EntityNotFoundException;
import ru.aav.model.Book;
import ru.aav.repository.BookRepository;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final ru.aav.catalogue.SectionService sections;

    public List<Book> findAll() {
        return bookRepository.findAll(
            org.springframework.data.domain.Sort.by("id")
        ).stream().filter(b -> !b.isArchived()).toList();
    }

    public Book findById(Long id) {
        log.debug("trying to find book with id {}", id);
        return bookRepository
            .findById(id)
            .filter(b -> !b.isArchived())
            .orElseThrow(() -> new EntityNotFoundException(id));
    }

    @Transactional
    public Book create(Book book) {
        log.debug("Book to create {}", book);
        book.setName(book.getName().strip());
        book.setAuthor(book.getAuthor().strip());
        if (book.getCategory() != null) book.setCategory(
            book.getCategory().strip()
        );
        book.setCategory(sections.ensureCatalog(book.getCategory()));
        return bookRepository.saveAndFlush(book);
    }
}
