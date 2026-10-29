package ru.aav.editorial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class EditorialController {
    private final EditorialService service;
    public record Edit(@Min(-1) long version, @NotNull @Size(max=3000) String description,
                       @NotNull @Size(max=8) List<@NotBlank @Size(max=50) String> lists,
                       @NotNull @Size(max=1000) String comment) {}
    @GetMapping("/api/editorial/books")
    public List<EditorialService.Book> books(@RequestParam(defaultValue="true") boolean recommended) {
        return service.list(recommended);
    }
    @GetMapping("/api/editorial/books/{kind}/{id}")
    public EditorialService.Book book(@PathVariable String kind, @PathVariable String id) {
        return service.get(kind,id);
    }
    @PutMapping("/api/admin/editorial/books/{kind}/{id}")
    public EditorialService.Book save(@PathVariable String kind, @PathVariable String id, @Valid @RequestBody Edit edit) {
        return service.save(kind,id,edit);
    }
}
