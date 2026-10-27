package ru.aav.shelf;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Все выборки полки ограничены владельцем. */
public interface ShelfRepository extends JpaRepository<ShelfEntry, Long> {
    List<ShelfEntry> findByUserIdOrderByIdAsc(Long userId);
    boolean existsByUserIdAndBookId(Long userId, Long bookId);
    void deleteByUserIdAndBookId(Long userId, Long bookId);
}
