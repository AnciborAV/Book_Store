package ru.aav.auth;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
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
