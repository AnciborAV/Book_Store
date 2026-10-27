package ru.aav.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.aav.model.BasicEntity;

/** Учётная запись. Хеш пароля никогда не передаётся в HTTP-ответах. */
@Entity
@Table(name = "store_users")
@Getter
@NoArgsConstructor
public class StoreUser extends BasicEntity {
    private String email;
    private String displayName;
    private String passwordHash;
    private byte[] avatar;

    public StoreUser(String email, String displayName, String passwordHash) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public void changeAvatar(byte[] avatar) { this.avatar = avatar; }
}
