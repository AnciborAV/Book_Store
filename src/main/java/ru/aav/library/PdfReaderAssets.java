package ru.aav.library;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Bundled PDF.js assets, served without external CDN requests. */
@RestController
public class PdfReaderAssets {
    private final Map<String, byte[]> assets;
    public PdfReaderAssets() throws IOException {
        var result = new HashMap<String, byte[]>();
        try (var resource = getClass().getResourceAsStream("/pdfjs-5.6.205.zip")) {
            if (resource == null) throw new IOException("Missing PDF.js distribution");
            try (var zip = new ZipInputStream(resource)) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (!entry.isDirectory()) result.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        assets = Map.copyOf(result);
    }
    @GetMapping("/pdfjs/5.6.205/{folder}/{name}")
    public ResponseEntity<byte[]> asset(@PathVariable String folder, @PathVariable String name) {
        byte[] bytes = assets.get(folder + "/" + name);
        if (bytes == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String type = name.endsWith(".mjs") ? "text/javascript" : name.endsWith(".css") ? "text/css"
            : name.endsWith(".wasm") ? "application/wasm" : name.endsWith(".ttf") ? "font/ttf" : "application/octet-stream";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()).body(bytes);
    }
}
