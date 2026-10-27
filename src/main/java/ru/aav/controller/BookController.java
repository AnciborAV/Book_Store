package ru.aav.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.aav.mapper.BookMapper;
import ru.aav.service.BookService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final BookMapper mapper;
    private final ru.aav.auth.AccountService accounts;

    /** Поиск каталога; личная выборка требует действующей сессии. */
    @GetMapping("/page")
    public BookPage search(@RequestParam(defaultValue = "") String query,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "12") int size,
                           @RequestParam(defaultValue = "false") boolean favorites,
                           java.security.Principal principal) {
        Long ownerId = null;
        if (favorites) {
            if (principal == null) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Войдите в аккаунт");
            ownerId = accounts.requireUser(principal.getName()).getId();
        }
        var result = bookService.search(query, page, size, ownerId);
        return new BookPage(result.getContent().stream().map(mapper::toDto).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }

    public record BookPage(List<BookDto> items, long total, int pages, int page) {}

    @GetMapping
    public ResponseEntity<List<BookDto>> findAll() {
        var result = bookService.findAll().stream()
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "{id}")
    public ResponseEntity<BookDto> findById(@PathVariable Long id) {
        log.info("trying to find book with id {}", id);
        var result = mapper.toDto(bookService.findById(id));
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<BookDto> create(@Valid @RequestBody CreateBookRequest book) {
        var entity = mapper.toEntity(book);
        var createdBook = bookService.create(entity);
        var result = mapper.toDto(createdBook);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(createdBook.getId())
                .toUri();
        return ResponseEntity.created(location).body(result);
    }
}
