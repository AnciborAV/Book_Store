package ru.aav.about;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AboutController {
    private final AboutService service;
    private final AboutImages images;
    @GetMapping({"/api/about","/api/admin/about"})
    public ResponseEntity<AboutService.Content> read() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read());
    }
    @GetMapping("/api/about/images/{slot}")
    public ResponseEntity<byte[]> image(@PathVariable String slot) throws IOException {
        var picture=service.image(slot);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(picture.contentType()))
            .cacheControl(CacheControl.noCache()).eTag("\"about-"+slot+"-"+picture.version()+"\"")
            .header("X-Content-Type-Options","nosniff").body(picture.bytes());
    }
    @PostMapping(value="/api/admin/about",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AboutService.Content save(@Valid @RequestPart("content") AboutService.Change content,
        @RequestPart(value="portrait",required=false) MultipartFile portrait,
        @RequestPart(value="development",required=false) MultipartFile development,
        @RequestPart(value="library",required=false) MultipartFile library) {
        var uploads=new HashMap<String,byte[]>();
        if(portrait!=null && !portrait.isEmpty()) uploads.put("portrait",images.process(portrait));
        if(development!=null && !development.isEmpty()) uploads.put("development",images.process(development));
        if(library!=null && !library.isEmpty()) uploads.put("library",images.process(library));
        return service.save(content,uploads);
    }
}
