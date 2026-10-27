package ru.aav.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.aav.mapper.BookMapper;
import ru.aav.service.BookService;

@Slf4j
@RestController
@RequestMapping("books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final BookMapper mapper;

    @GetMapping
    public ResponseEntity<List<BookDto>> findAll() {
        var result = bookService.findAll().stream().map(mapper::toDto).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "{id}")
    public ResponseEntity<BookDto> findById(@PathVariable Long id) {
        log.info("trying to find book with id {}", id);
        var result = mapper.toDto(bookService.findById(id));
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<BookDto> create(
        @Valid @RequestBody CreateBookRequest book
    ) {
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
