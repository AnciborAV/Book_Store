package ru.aav;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import ru.aav.auth.*;
import ru.aav.catalog.SelectionService;
import ru.aav.commerce.*;
import ru.aav.model.Book;
import ru.aav.repository.BookRepository;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:h2:mem:commerce;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "bookstore.admin.email=bootstrap@example.com",
        "bookstore.admin.password=bootstrap-test-password",
    }
)
@AutoConfigureMockMvc
class CommerceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountService accounts;

    @Autowired
    UserRepository users;

    @Autowired
    BookRepository books;

    @Autowired
    InventoryService inventory;

    @Autowired
    SelectionService selection;

    @Autowired
    OrderService orders;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    String buyer, other, admin;
    long book;

    @BeforeEach
    void setup() {
        buyer = register("buyer");
        other = register("other");
        admin = register("admin");
        var a = users.findByEmail(admin).orElseThrow();
        a.setRole("ADMIN");
        users.saveAndFlush(a);
        var b = new Book();
        b.setName("Commerce " + UUID.randomUUID());
        b.setAuthor("Author");
        book = books.saveAndFlush(b).getId();
        inventory.update(
            admin,
            book,
            b.getName(),
            "Author",
            "История",
            new BigDecimal("10.25")
        );
        inventory.receive(admin, book, 5, UUID.randomUUID(), "Накладная 1");
    }

    String register(String prefix) {
        String email = prefix + UUID.randomUUID() + "@example.com";
        accounts.register(new Registration(email, prefix, "test-password"));
        return email;
    }

    @Test
    void rolesCannotBeGrantedByRegistrationAndAdminApiIsProtected()
        throws Exception {
        String email = "spoof" + UUID.randomUUID() + "@example.com";
        mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\"" +
                        email +
                        "\",\"displayName\":\"Reader\",\"password\":\"test-password\",\"role\":\"ADMIN\"}"
                )
        ).andExpect(status().isCreated());
        assertThat(users.findByEmail(email).orElseThrow().getRole()).isEqualTo(
            "CUSTOMER"
        );
        assertThat(
            users.findByEmail("bootstrap@example.com").orElseThrow().getRole()
        ).isEqualTo("ADMIN");
        mvc.perform(get("/api/admin/inventory")).andExpect(
            status().isUnauthorized()
        );
        mvc.perform(
            get("/api/admin/inventory").with(user(buyer).roles("CUSTOMER"))
        ).andExpect(status().isForbidden());
        mvc.perform(
            post("/books")
                .with(csrf())
                .with(user(buyer).roles("CUSTOMER"))
                .contentType("application/json")
                .content("{\"name\":\"No\",\"author\":\"No\"}")
        ).andExpect(status().isForbidden());
        mvc.perform(
            get("/api/admin/inventory").with(user(admin).roles("ADMIN"))
        ).andExpect(status().isOk());
        mvc.perform(
            post("/api/orders")
                .with(user(buyer))
                .contentType("application/json")
                .content("{\"requestKey\":\"" + UUID.randomUUID() + "\"}")
        ).andExpect(status().isForbidden());
        mvc.perform(
            post("/api/admin/books/" + book + "/receipts")
                .with(user(admin).roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"quantity\":-1,\"note\":\"bad\",\"requestKey\":\"" +
                        UUID.randomUUID() +
                        "\"}"
                )
        ).andExpect(status().isBadRequest());
    }

    @Test
    void checkoutIsIdempotentPreservesPriceAndFulfilsThroughValidStates()
        throws Exception {
        selection.quantity(buyer, book, 2);
        UUID key = UUID.randomUUID();
        String payload = json.writeValueAsString(
            new CommerceController.Checkout(key)
        );
        var response = mvc
            .perform(
                post("/api/orders")
                    .with(user(buyer))
                    .with(csrf())
                    .contentType("application/json")
                    .content(payload)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
        String id = json
            .readTree(response.getContentAsString())
            .get("order")
            .get("id")
            .asText();
        assertThat(orders.checkout(buyer, key).order().id()).isEqualTo(id);
        assertThat(selection.get(buyer).cart()).isEmpty();
        assertThat(books.findById(book).orElseThrow().getReserved()).isEqualTo(
            2
        );
        inventory.update(
            admin,
            book,
            "Renamed",
            "New author",
            "История",
            new BigDecimal("99.99")
        );
        var detail = orders.detail(buyer, id);
        assertThat(detail.order().total()).isEqualByComparingTo("20.50");
        assertThat(detail.items().getFirst().unitPrice()).isEqualByComparingTo(
            "10.25"
        );
        assertThat(detail.items().getFirst().name()).startsWith("Commerce ");
        mvc.perform(get("/api/orders/" + id).with(user(other))).andExpect(
            status().isNotFound()
        );
        assertThat(orders.list(other, 0)).isEmpty();
        assertThatThrownBy(() ->
            orders.transition(admin, id, OrderStatus.COMPLETED, true)
        ).isInstanceOf(ResponseStatusException.class);
        for (var status : new OrderStatus[] {
            OrderStatus.CONFIRMED,
            OrderStatus.PACKED,
            OrderStatus.SHIPPED,
            OrderStatus.COMPLETED,
        })
            orders.transition(admin, id, status, true);
        orders.transition(admin, id, OrderStatus.COMPLETED, true);
        assertThat(books.findById(book).orElseThrow().getStock()).isEqualTo(3);
        assertThat(books.findById(book).orElseThrow().getReserved()).isZero();
        assertThat(orders.detail(buyer, id).events()).hasSize(5);
        assertThatThrownBy(() ->
            orders.transition(buyer, id, OrderStatus.CANCELLED, false)
        ).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cancellationReleasesReserveExactlyOnceAndRejectsOtherOwner() {
        selection.quantity(buyer, book, 3);
        String id = orders.checkout(buyer, UUID.randomUUID()).order().id();
        assertThatThrownBy(() ->
            orders.transition(other, id, OrderStatus.CANCELLED, false)
        ).isInstanceOf(ResponseStatusException.class);
        orders.transition(buyer, id, OrderStatus.CANCELLED, false);
        orders.transition(buyer, id, OrderStatus.CANCELLED, false);
        var b = books.findById(book).orElseThrow();
        assertThat(b.getStock()).isEqualTo(5);
        assertThat(b.getReserved()).isZero();
        assertThat(orders.detail(buyer, id).events()).hasSize(2);
    }

    @Test
    void failedCheckoutRollsBackAndRetainsCart() {
        selection.quantity(buyer, book, 6);
        assertThatThrownBy(() ->
            orders.checkout(buyer, UUID.randomUUID())
        ).isInstanceOf(ResponseStatusException.class);
        assertThat(orders.list(buyer, 0)).isEmpty();
        assertThat(selection.get(buyer).cart()).containsEntry(book, 6);
        assertThat(books.findById(book).orElseThrow().getReserved()).isZero();
        var b = new Book();
        b.setName("Unpriced " + UUID.randomUUID());
        b.setAuthor("Author");
        long otherBook = books.saveAndFlush(b).getId();
        selection.quantity(buyer, book, 1);
        selection.quantity(buyer, otherBook, 1);
        assertThatThrownBy(() ->
            orders.checkout(buyer, UUID.randomUUID())
        ).isInstanceOf(ResponseStatusException.class);
        assertThat(books.findById(book).orElseThrow().getReserved()).isZero();
        assertThat(selection.get(buyer).cart()).hasSize(2);
    }

    @Test
    void receiptsAreAuditedIdempotentAndCartDatesAreRecorded() {
        var key = UUID.randomUUID();
        inventory.receive(admin, book, 2, key, "Поставка");
        inventory.receive(admin, book, 2, key, "Поставка");
        assertThat(books.findById(book).orElseThrow().getStock()).isEqualTo(7);
        assertThatThrownBy(() ->
            inventory.receive(admin, book, 3, key, "Поставка")
        ).isInstanceOf(ResponseStatusException.class);
        assertThat(inventory.movements(admin, 0)).anyMatch(
            m ->
                m.bookId() == book &&
                m.stockDelta() == 2 &&
                m.kind().equals("RECEIPT") &&
                m.createdAt() != null
        );
        selection.quantity(buyer, book, 1);
        selection.quantity(buyer, book, 2);
        var row = jdbc.queryForMap(
            "SELECT * FROM cart_details WHERE email=?",
            buyer
        );
        assertThat(row.get("added_at")).isNotNull();
        assertThat(row.get("updated_at")).isNotNull();
        assertThat(row.get("book_title")).isNotNull();
    }

    @Test
    void twoBuyersCannotReserveTheSameLastCopy() throws Exception {
        var last = new Book();
        last.setName("Last " + UUID.randomUUID());
        last.setAuthor("Author");
        long id = books.saveAndFlush(last).getId();
        inventory.update(
            admin,
            id,
            last.getName(),
            "Author",
            "История",
            new BigDecimal("0.10")
        );
        inventory.receive(
            admin,
            id,
            1,
            UUID.randomUUID(),
            "Последний экземпляр"
        );
        selection.quantity(buyer, id, 1);
        selection.quantity(other, id, 1);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = new java.util.ArrayList<Future<Boolean>>();
            for (String email : java.util.List.of(buyer, other))
                results.add(
                    pool.submit(() -> {
                        start.await();
                        try {
                            orders.checkout(email, UUID.randomUUID());
                            return true;
                        } catch (ResponseStatusException e) {
                            if (e.getStatusCode().value() != 409) throw e;
                            return false;
                        }
                    })
                );
            start.countDown();
            int successes = 0;
            for (var result : results)
                if (result.get(20, TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        var b = books.findById(id).orElseThrow();
        assertThat(b.getReserved()).isEqualTo(1);
        assertThat(b.getStock()).isEqualTo(1);
    }
}
