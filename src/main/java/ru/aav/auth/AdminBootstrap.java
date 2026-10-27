package ru.aav.auth;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit local operator configuration; public registration always creates CUSTOMER. */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final AccountService accounts;
    private final Validator validator;

    @Value("${bookstore.admin.email:}")
    private String email;

    @Value("${bookstore.admin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank()) return;
        String normalized = CurrentAccount.normalize(email);
        if (users.findByEmail(normalized).isEmpty()) {
            var registration = new Registration(
                normalized,
                "Администратор",
                password
            );
            if (!validator.validate(registration).isEmpty()) {
                throw new IllegalStateException(
                    "Для нового администратора задайте корректные BOOKSTORE_ADMIN_EMAIL и BOOKSTORE_ADMIN_PASSWORD (8–72 символа)"
                );
            }
            accounts.register(registration);
        }
        users.findByEmail(normalized).orElseThrow().setRole("ADMIN");
        // Never overwrite an existing user's password or grant roles from request JSON.
    }
}
