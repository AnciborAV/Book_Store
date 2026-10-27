package ru.aav.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/selection")
@RequiredArgsConstructor
public class SelectionController {
    private final SelectionService selection;
    public record Quantity(@Min(1) @Max(99) int quantity) {}

    @GetMapping
    public Selection get(Principal user) { return selection.get(user.getName()); }

    @PutMapping("/favorites/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favorite(Principal user, @PathVariable long bookId) { selection.favorite(user.getName(), bookId); }

    @DeleteMapping("/favorites/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavorite(Principal user, @PathVariable long bookId) { selection.unfavorite(user.getName(), bookId); }

    @PutMapping("/cart/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quantity(Principal user, @PathVariable long bookId, @Valid @RequestBody Quantity body) {
        selection.quantity(user.getName(), bookId, body.quantity());
    }

    @DeleteMapping("/cart/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Principal user, @PathVariable long bookId) { selection.remove(user.getName(), bookId); }

    @DeleteMapping("/cart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Principal user) { selection.clear(user.getName()); }
}
