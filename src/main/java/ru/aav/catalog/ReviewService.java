package ru.aav.catalog;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.auth.CurrentAccount;
import ru.aav.service.BookService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviews;
    private final CurrentAccount accounts;
    private final BookService books;

    public List<Review> list(long book, String email) {
        books.findById(book);
        return reviews.find(
            book,
            email == null ? null : accounts.require(email).getId()
        );
    }

    @Transactional
    public void save(long book, String email, int rating, String body) {
        long user = accounts.lock(email).getId();
        books.findById(book);
        reviews.save(book, user, rating, body.strip());
    }

    @Transactional
    public void delete(long book, long review, String email) {
        if (!reviews.delete(book, review, accounts.lock(email).getId())) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Отзыв не найден"
            );
        }
    }
}
