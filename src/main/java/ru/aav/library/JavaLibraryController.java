package ru.aav.library;

import jakarta.validation.constraints.*;
import java.util.UUID;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequiredArgsConstructor
public class JavaLibraryController {
    private final JavaLibraryService library;
    private final LibraryFiles files;

    @GetMapping("/api/java-library")
    public JavaLibraryService.Page list(@RequestParam(defaultValue="0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue="") @Size(max=255) String q,
        @RequestParam(defaultValue="java") @Pattern(regexp="java|civil-law|ai") String section) { return library.list(page,q,section); }

    @GetMapping("/api/java-library/metadata")
    public java.util.List<JavaLibraryService.Book> metadata(@RequestParam @Size(max=12) java.util.List<UUID> ids) {
        return library.metadata(ids);
    }

    @GetMapping("/api/java-library/{id}/download")
    public void download(@PathVariable UUID id, HttpServletResponse response) throws IOException {
        library.download(id.toString(),response);
    }
    @GetMapping("/api/java-library/{id}/content")
    public void content(@PathVariable UUID id, @RequestParam @Min(0) long version,
        @RequestHeader(value="Range", required=false) String range, HttpServletResponse response) throws IOException {
        library.read(id.toString(), version, range, response);
    }
    @GetMapping("/api/java-library/{id}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable UUID id) {
        var file=library.cover(id.toString());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noCache()).body(file.bytes());
    }
    @PostMapping(value="/api/admin/java-library",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public JavaLibraryService.Book create(@RequestParam @NotBlank @Size(max=255) String title,
        @RequestParam(defaultValue="") @Size(max=255) String author,
        @RequestParam(defaultValue="") @Size(max=3000) String description,
        @RequestParam(defaultValue="java") @Pattern(regexp="java|civil-law|ai") String section,
        @RequestParam MultipartFile pdf, @RequestParam(required=false) MultipartFile cover) throws IOException {
        try (var prepared=files.prepare(pdf,cover)) {
            return library.create(title,author,description,section,prepared);
        }
    }
    @PostMapping(value="/api/admin/java-library/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public JavaLibraryService.Book update(@PathVariable UUID id, @RequestParam @Min(0) long version,
        @RequestParam @NotBlank @Size(max=255) String title,
        @RequestParam(defaultValue="") @Size(max=255) String author,
        @RequestParam(defaultValue="") @Size(max=3000) String description,
        @RequestParam(required=false) MultipartFile pdf,@RequestParam(required=false) MultipartFile cover) throws IOException {
        try (var prepared=files.prepare(pdf,cover)) {
            return library.update(id.toString(),version,title,author,description,prepared);
        }
    }
    @DeleteMapping("/api/admin/java-library/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,@RequestParam @Min(0) long version) { library.delete(id.toString(),version); }
}
