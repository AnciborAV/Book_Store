package ru.aav.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final CurrentAccount accounts;

    @Transactional
    public void register(Registration request) {
        // BCrypt's limit is bytes, not Unicode characters.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль должен занимать не более 72 байт UTF-8");
        }
        String email = CurrentAccount.normalize(request.email());
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Эта почта уже зарегистрирована");
        }
        var user = new StoreUser();
        user.setEmail(email);
        user.setDisplayName(request.displayName().strip());
        user.setPasswordHash(passwords.encode(request.password()));
        users.saveAndFlush(user);
    }

    public Profile profile(String email) { return Profile.from(accounts.require(email)); }
    public byte[] avatar(String email) { return accounts.require(email).getAvatar(); }

    @Transactional
    public Profile saveAvatar(String email, byte[] image) {
        var user = accounts.lock(email);
        user.setAvatar(image);
        return Profile.from(user);
    }
}
