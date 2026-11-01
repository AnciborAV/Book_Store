package ru.aav.editorial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.aav.text.TextStyles.Mark;

@RestController
@RequiredArgsConstructor
public class EditorialController {
    private final EditorialService service;
    private final AuthorProfiles authors;

    public record Edit(
        @Min(-1) long version,
        @NotNull @Size(max = 3000) String description,
        @NotNull @Size(max = 8) List<@NotBlank @Size(max = 50) String> lists,
        @NotNull String comment,
        @Size(max = 5000) List<@Valid Mark> descriptionStyle,
        @Size(max = 5000) List<@Valid Mark> commentStyle
    ) {}

    @GetMapping("/api/editorial/books")
    public List<EditorialService.Book> books(@RequestParam(defaultValue = "true") boolean recommended) {
        return service.list(recommended);
    }

    @GetMapping("/api/editorial/books/{kind}/{id}")
    public EditorialService.Book book(@PathVariable String kind, @PathVariable String id) {
        return service.get(kind, id);
    }

    @GetMapping("/api/editorial/books/{kind}/{id}/authors")
    public AuthorProfiles.Detail authors(@PathVariable String kind, @PathVariable String id) {
        var book = service.get(kind, id);
        return authors.find(book.title(), book.author());
    }

    @PutMapping("/api/admin/editorial/books/{kind}/{id}")
    public EditorialService.Book save(
        @PathVariable String kind,
        @PathVariable String id,
        @Valid @RequestBody Edit edit
    ) {
        return service.save(kind, id, edit);
    }
}
