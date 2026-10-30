package ru.aav.mapper;

import org.mapstruct.Mapper;
import ru.aav.controller.BookDto;
import ru.aav.controller.CreateBookRequest;
import ru.aav.model.Book;

@Mapper
public interface BookMapper {
    @org.mapstruct.Mapping(
        target = "availableStock",
        expression = "java(book.getStock() - book.getReserved())"
    )
    BookDto toDto(Book book);

    // Database-managed fields have no setters and cannot be populated from a request.
    @org.mapstruct.Mapping(target = "price", ignore = true)
    @org.mapstruct.Mapping(target = "stock", ignore = true)
    @org.mapstruct.Mapping(target = "reserved", ignore = true)
    @org.mapstruct.Mapping(target = "retiredTitle", ignore = true)
    @org.mapstruct.Mapping(target = "archived", ignore = true)
    @org.mapstruct.Mapping(target = "managementRevision", ignore = true)
    Book toEntity(CreateBookRequest request);
}
