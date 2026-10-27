package ru.aav.shelf;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aav.auth.AccountService;
import ru.aav.service.BookService;
import java.util.List;

/** Полка определяется сессией, а не переданным клиентом userId. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShelfService {
    private final AccountService accounts;
    private final ShelfRepository shelves;
    private final BookService books;

    public List<Long> list(String email) {
        return shelves.findByUserIdOrderByIdAsc(accounts.requireUser(email).getId())
                .stream().map(ShelfEntry::getBookId).toList();
    }

    @Transactional
    public void add(String email, Long bookId) {
        var userId = accounts.requireUser(email).getId();
        books.findById(bookId);
        if (!shelves.existsByUserIdAndBookId(userId, bookId)) {
            shelves.saveAndFlush(new ShelfEntry(userId, bookId));
        }
    }

    @Transactional
    public void remove(String email, Long bookId) {
        shelves.deleteByUserIdAndBookId(accounts.requireUser(email).getId(), bookId);
    }
}
