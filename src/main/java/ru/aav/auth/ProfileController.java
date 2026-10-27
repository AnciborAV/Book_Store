package ru.aav.auth;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {

    private final AccountService accounts;
    private final AvatarProcessor images;

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
            "token",
            token.getToken(),
            "headerName",
            token.getHeaderName()
        );
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody Registration request) {
        accounts.register(request);
    }

    @GetMapping("/profile")
    public Profile profile(Principal principal) {
        return accounts.profile(principal.getName());
    }

    @GetMapping(value = "/profile/avatar", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> avatar(Principal principal) {
        byte[] image = accounts.avatar(principal.getName());
        return image == null
            ? ResponseEntity.notFound().build()
            : ResponseEntity.ok()
                  .cacheControl(CacheControl.noStore())
                  .body(image);
    }

    @PutMapping("/profile/avatar")
    public Profile upload(
        Principal principal,
        @RequestParam("file") MultipartFile file
    ) {
        // Decode outside the database transaction, keeping locks short.
        return accounts.saveAvatar(principal.getName(), images.process(file));
    }
}
