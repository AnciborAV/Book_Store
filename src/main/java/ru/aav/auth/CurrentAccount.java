package ru.aav.auth;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Resolves account identity exclusively from the authenticated principal. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentAccount {

    private final UserRepository users;

    public StoreUser require(String email) {
        return users
            .findByEmail(normalize(email))
            .orElseThrow(CurrentAccount::unauthorized);
    }

    /** Serializes account mutations, including concurrent insert-or-update operations. */
    @Transactional
    public StoreUser lock(String email) {
        return users
            .findLockedByEmail(normalize(email))
            .orElseThrow(CurrentAccount::unauthorized);
    }

    public static String normalize(String email) {
        if (email == null) throw unauthorized();
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Войдите в аккаунт"
        );
    }
}
