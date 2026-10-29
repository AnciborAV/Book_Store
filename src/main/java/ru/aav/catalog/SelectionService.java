package ru.aav.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aav.auth.CurrentAccount;
import ru.aav.service.BookService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SelectionService {

    private final SelectionRepository selection;
    private final CurrentAccount accounts;
    private final BookService books;

    public Selection get(String email) {
        return selection.find(accounts.require(email).getId());
    }

    @Transactional
    public void favorite(String email, long book) {
        long user = accounts.lock(email).getId();
        books.findById(book);
        selection.favorite(user, book);
    }

    @Transactional
    public void unfavorite(String email, long book) {
        selection.unfavorite(accounts.lock(email).getId(), book);
    }

    @Transactional
    public void libraryFavorite(String email, String book) {
        selection.libraryFavorite(accounts.lock(email).getId(), book);
    }
    @Transactional
    public void libraryUnfavorite(String email, String book) {
        selection.libraryUnfavorite(accounts.lock(email).getId(), book);
    }

    @Transactional
    public void quantity(String email, long book, int quantity) {
        long user = accounts.lock(email).getId();
        books.findById(book);
        selection.quantity(user, book, quantity);
    }

    @Transactional
    public void remove(String email, long book) {
        selection.remove(accounts.lock(email).getId(), book);
    }

    @Transactional
    public void clear(String email) {
        selection.clear(accounts.lock(email).getId());
    }
}
