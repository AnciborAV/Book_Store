package ru.aav.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Поиск учётной записи по нормализованной электронной почте. */
public interface UserRepository extends JpaRepository<StoreUser, Long> {
    Optional<StoreUser> findByEmail(String email);
}
