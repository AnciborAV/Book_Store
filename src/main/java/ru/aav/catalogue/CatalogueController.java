package ru.aav.catalogue;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.aav.library.LibraryFiles;

@RestController
@Validated
@RequiredArgsConstructor
public class CatalogueController {
    private final CatalogueService catalogue;
    private final SectionService sections;
    private final LibraryFiles files;
    public record NewSection(@NotBlank @Size(max=80) String name) {}
    public record SectionEdit(@NotBlank @Size(max=80) String name,@Min(0) int position,@Min(0) long version) {}
    @GetMapping("/api/catalogue") public CatalogueService.Catalogue list(){return catalogue.list();}
    @GetMapping("/api/catalogue/books/{kind}/{id}") public CatalogueService.Entry get(@PathVariable String kind,@PathVariable String id){return catalogue.get(kind,id);}
    @GetMapping("/api/catalogue/covers/{id}") public ResponseEntity<byte[]> cover(@PathVariable long id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noCache()).body(catalogue.cover(id).bytes());
    }
    @PostMapping("/api/admin/catalogue/sections") @ResponseStatus(HttpStatus.CREATED)
    public SectionService.Section create(@Valid @RequestBody NewSection input){return sections.create(input.name());}
    @PutMapping("/api/admin/catalogue/sections/{id}") public SectionService.Section updateSection(@PathVariable String id,@Valid @RequestBody SectionEdit input){return sections.update(id,input.name(),input.position(),input.version());}
    @DeleteMapping("/api/admin/catalogue/sections/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSection(@PathVariable String id,@RequestParam @Min(0) long version){sections.delete(id,version);}
    @PostMapping(value="/api/admin/catalogue/books",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public void createBook(@RequestParam @NotBlank @Size(max=255) String title,@RequestParam(defaultValue="") @Size(max=255) String author,
        @RequestParam @NotBlank @Size(max=80) String sectionId,@RequestParam(defaultValue="") @Size(max=3000) String description,
        @RequestParam(required=false) MultipartFile pdf,@RequestParam(required=false) MultipartFile cover)throws IOException {
        try(var prepared=files.prepare(pdf,cover)){catalogue.create(title,author,sectionId,description,prepared,cover!=null&&!cover.isEmpty());}
    }
    @PostMapping(value="/api/admin/catalogue/books/{kind}/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateBook(@PathVariable String kind,@PathVariable String id,@RequestParam @Min(0) long version,
        @RequestParam(defaultValue="0") @Min(0) long pdfVersion,@RequestParam @NotBlank @Size(max=255) String title,
        @RequestParam(defaultValue="") @Size(max=255) String author,@RequestParam @NotBlank @Size(max=80) String sectionId,
        @RequestParam(defaultValue="") @Size(max=3000) String description,@RequestParam(defaultValue="false") boolean removePdf,
        @RequestParam(required=false) MultipartFile pdf,@RequestParam(required=false) MultipartFile cover)throws IOException {
        try(var prepared=files.prepare(pdf,cover)){catalogue.update(kind,id,version,pdfVersion,title,author,sectionId,description,prepared,cover!=null&&!cover.isEmpty(),removePdf);}
    }
    @DeleteMapping("/api/admin/catalogue/books/{kind}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBook(@PathVariable String kind,@PathVariable String id,@RequestParam @Min(0) long version,
        @RequestParam(defaultValue="0") @Min(0) long pdfVersion){catalogue.delete(kind,id,version,pdfVersion);}
}
