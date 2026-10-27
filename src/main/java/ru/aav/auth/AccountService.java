package ru.aav.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Создание аккаунтов и доступ к текущему пользователю без раскрытия секретов. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    @Transactional
    public void register(RegisterRequest request) {
        // BCrypt ограничивает пароль 72 байтами, а не 72 символами Unicode.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль должен занимать не более 72 байт UTF-8");
        }
        users.saveAndFlush(new StoreUser(normalizeEmail(request.email()),
                request.displayName().strip(), passwords.encode(request.password())));
    }

    public StoreUser requireUser(String email) {
        return users.findByEmail(normalizeEmail(email)).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Войдите в аккаунт"));
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
