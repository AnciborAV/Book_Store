package ru.aav.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviews;

    public record ReviewRequest(
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(max = 2000) String body
    ) {}

    @GetMapping
    public List<Review> list(@PathVariable long bookId, Principal user) {
        return reviews.list(bookId, user == null ? null : user.getName());
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(
        @PathVariable long bookId,
        Principal user,
        @Valid @RequestBody ReviewRequest body
    ) {
        reviews.save(bookId, user.getName(), body.rating(), body.body());
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable long bookId,
        @PathVariable long reviewId,
        Principal user
    ) {
        reviews.delete(bookId, reviewId, user.getName());
    }
}
