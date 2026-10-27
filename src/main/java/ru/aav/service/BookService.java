package ru.aav.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aav.exception.EntityNotFoundException;
import ru.aav.model.Book;
import ru.aav.repository.BookRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
@Slf4j
@RequiredArgsConstructor
public class BookService {

    /** Размер страницы ограничен, чтобы запрос не загружал весь каталог в память. */
    public org.springframework.data.domain.Page<Book> search(String query, int page, int size, Long ownerId) {
        if (page < 0 || size < 1 || size > 100 || query.length() > 255) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Некорректные параметры поиска");
        }
        return bookRepository.search(query.strip(), ownerId,
                org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("id")));
    }


    private final BookRepository bookRepository;

    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    public Book findById(Long id) {
        log.info("trying to find book with id {}", id);
        return bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(id));
    }

    @Transactional
    public Book create(Book book) {
        book.setName(book.getName().strip());
        book.setAuthor(book.getAuthor().strip());
        return bookRepository.save(book);

    }
}
