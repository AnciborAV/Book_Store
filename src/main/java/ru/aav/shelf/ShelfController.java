package ru.aav.shelf;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import java.util.List;

/** Чтение и изменение личной полки после входа. */
@RestController
@RequestMapping("/api/shelf")
@RequiredArgsConstructor
public class ShelfController {
    private final ShelfService shelves;

    @GetMapping
    public List<Long> list(Principal principal) { return shelves.list(principal.getName()); }

    @PutMapping("/{bookId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(Principal principal, @PathVariable Long bookId) { shelves.add(principal.getName(), bookId); }

    @DeleteMapping("/{bookId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Principal principal, @PathVariable Long bookId) { shelves.remove(principal.getName(), bookId); }
}
