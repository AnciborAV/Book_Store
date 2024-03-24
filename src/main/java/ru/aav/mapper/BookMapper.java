package ru.aav.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.aav.controller.BookDto;
import ru.aav.model.Book;

@Mapper
public interface BookMapper {

    BookDto toDto(Book book);

    @Mapping(target = "version", ignore = true)
    @Mapping(target = "created", ignore = true)
    Book toEntity(BookDto dto);
}
