package ru.aav.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviews;
    public record ReviewRequest(@Min(1) @Max(5) int rating, @NotBlank @Size(max=2000) String body) {}

    @GetMapping
    public List<Review> list(@PathVariable long bookId, Principal user) {
        return reviews.list(bookId, user == null ? null : user.getName());
    }
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@PathVariable long bookId, Principal user, @Valid @RequestBody ReviewRequest body) {
        reviews.save(bookId, user.getName(), body.rating(), body.body());
    }
    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long bookId, @PathVariable long reviewId, Principal user) {
        reviews.delete(bookId, reviewId, user.getName());
    }
}
