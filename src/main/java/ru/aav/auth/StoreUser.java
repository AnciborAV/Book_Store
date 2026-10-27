package ru.aav.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ru.aav.model.BasicEntity;

@Entity
@Table(name = "store_users")
@Getter
@Setter
public class StoreUser extends BasicEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role = "CUSTOMER";

    @Column(columnDefinition = "bytea")
    private byte[] avatar;
}
