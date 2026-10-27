package ru.aav.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import ru.aav.auth.AccountService;

/** Операции профиля всегда относятся к пользователю из серверной сессии. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {
    private final AccountService accounts;

    public ProfileDto profile(String email) {
        var user = accounts.requireUser(email);
        return new ProfileDto(user.getEmail(), user.getDisplayName(), user.getAvatar() != null);
    }

    public byte[] avatar(String email) {
        var data = accounts.requireUser(email).getAvatar();
        if (data == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Аватарка не загружена");
        return data;
    }

    @Transactional
    public ProfileDto changeAvatar(String email, byte[] image) {
        accounts.requireUser(email).changeAvatar(image);
        return profile(email);
    }

    public record ProfileDto(String email, String displayName, boolean hasAvatar) {}
}
