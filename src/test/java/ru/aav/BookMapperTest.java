package ru.aav;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import ru.aav.controller.CreateBookRequest;
import ru.aav.mapper.BookMapper;
import ru.aav.model.Book;

class BookMapperTest {

    private final BookMapper mapper = Mappers.getMapper(BookMapper.class);

    @Test
    void requestMapsBusinessFieldsAndLeavesIdForDatabase() {
        Book book = mapper.toEntity(new CreateBookRequest("Java", "Author"));
        assertThat(book.getName()).isEqualTo("Java");
        assertThat(book.getAuthor()).isEqualTo("Author");
        assertThat(book.getId()).isNull();
        assertThat(mapper.toDto(book).name()).isEqualTo("Java");
        assertThat(mapper.toDto(book).author()).isEqualTo("Author");
    }

    @Test
    void distinctTransientBooksAreNotEqual() {
        Book first = new Book();
        Book second = new Book();
        assertThat(first)
            .isEqualTo(first)
            .isNotEqualTo(second)
            .isNotEqualTo(null);
    }
}
