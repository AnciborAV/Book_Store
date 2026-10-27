package ru.aav.shelf;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Связь пользователя с книгой; уникальность пары дополнительно обеспечивает база. */
@Entity
@Table(name = "shelf_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_id"}))
@Getter
@NoArgsConstructor
public class ShelfEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "book_id", nullable = false)
    private Long bookId;

    public ShelfEntry(Long userId, Long bookId) { this.userId = userId; this.bookId = bookId; }
}
