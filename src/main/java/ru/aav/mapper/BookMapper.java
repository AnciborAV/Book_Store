package ru.aav.mapper;

import org.mapstruct.Mapper;
import ru.aav.controller.BookDto;
import ru.aav.controller.CreateBookRequest;
import ru.aav.model.Book;

@Mapper
public interface BookMapper {
    BookDto toDto(Book book);
    // Database-managed fields have no setters and cannot be populated from a request.
    Book toEntity(CreateBookRequest request);
}
