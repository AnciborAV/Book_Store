package ru.aav.commerce;

import static ru.aav.commerce.CommerceDto.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.auth.CurrentAccount;
import ru.aav.auth.StoreUser;
import ru.aav.model.Book;
import ru.aav.repository.BookRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final BookRepository books;
    private final CommerceRepository repository;
    private final CurrentAccount accounts;
    private final ru.aav.catalogue.CatalogueOrderService ordering;

    public StoreUser admin(String email) {
        var user = accounts.require(email);
        if (!"ADMIN".equals(user.getRole())) throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Нужны права администратора"
        );
        return user;
    }

    public Book lock(long id) {
        return books
            .findLockedById(id)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Книга не найдена"
                )
            );
    }

    public List<Stock> list(String email) {
        admin(email);
        return repository.stock();
    }

    public List<Movement> movements(String email, int page) {
        admin(email);
        return repository.movements(page);
    }

    @Transactional
    public void update(
        String email,
        long id,
        String name,
        String author,
        String category,
        BigDecimal price
    ) {
        admin(email);
        ordering.lock();
        var book = lock(id);
        book.setName(name.strip());
        book.setAuthor(author.strip());
        book.setCategory(category == null ? null : category.strip());
        book.setPrice(price);
        book.setManagementRevision(book.getManagementRevision()+1);
        books.flush();
    }

    @Transactional
    public void receive(
        String email,
        long id,
        int quantity,
        UUID key,
        String note
    ) {
        var actor = admin(email);
        var book = lock(id);
        if (
            repository.receiptExists(
                actor.getId(),
                key.toString(),
                id,
                quantity,
                note.strip()
            )
        ) return;
        if (
            quantity > Integer.MAX_VALUE - book.getStock()
        ) throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Превышен допустимый остаток"
        );
        book.setStock(book.getStock() + quantity);
        repository.movement(
            book,
            actor.getId(),
            "RECEIPT",
            quantity,
            0,
            null,
            key.toString(),
            note.strip()
        );
    }
}
