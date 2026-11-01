package ru.aav.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.aav.controller.BookDto;
import ru.aav.controller.CreateBookRequest;
import ru.aav.model.Book;

@Mapper
public interface BookMapper {
    @Mapping(
        target = "availableStock",
        expression = "java(book.getStock() - book.getReserved())"
    )
    BookDto toDto(Book book);

    // Database-managed fields have no setters and cannot be populated from a request.
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "stock", ignore = true)
    @Mapping(target = "reserved", ignore = true)
    @Mapping(target = "retiredTitle", ignore = true)
    @Mapping(target = "archived", ignore = true)
    @Mapping(target = "managementRevision", ignore = true)
    Book toEntity(CreateBookRequest request);
}
