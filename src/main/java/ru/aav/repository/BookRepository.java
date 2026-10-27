package ru.aav.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import ru.aav.model.Book;

/** Поиск и постраничная выдача выполняются в базе, а не в браузере. */
public interface BookRepository extends JpaRepository<Book, Long> {
    @Query("""
            select b from Book b
            where (locate(lower(:query), lower(b.name)) > 0
                or locate(lower(:query), lower(b.author)) > 0)
            and (:ownerId is null or exists
                (select s.id from ShelfEntry s where s.bookId = b.id and s.userId = :ownerId))
            """)
    Page<Book> search(@Param("query") String query, @Param("ownerId") Long ownerId, Pageable pageable);
}
