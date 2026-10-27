package ru.aav.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    public record Registration(@NotBlank @Email @Size(max=255) String email,
        @NotBlank @Size(max=80) String displayName, @NotBlank @Size(min=8,max=72) String password) {}
    public record Profile(String email, String displayName, boolean hasAvatar) {}
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody Registration request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Эта почта уже зарегистрирована");
        var user = new StoreUser();
        user.setEmail(email); user.setDisplayName(request.displayName().strip());
        user.setPasswordHash(passwords.encode(request.password()));
        users.saveAndFlush(user);
    }
    private StoreUser current(Authentication authentication) {
        return users.findByEmail(authentication.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    @GetMapping("/profile") public Profile profile(Authentication authentication) {
        var u = current(authentication); return new Profile(u.getEmail(), u.getDisplayName(), u.getAvatar()!=null);
    }
    @GetMapping(value="/profile/avatar", produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> avatar(Authentication authentication) {
        var bytes = current(authentication).getAvatar();
        if (bytes == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(bytes);
    }
    @PutMapping("/profile/avatar") @Transactional
    public Profile upload(Authentication authentication, @RequestParam("file") MultipartFile file) throws IOException {
        if(file.isEmpty() || file.getSize()>2*1024*1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите изображение до 2 МБ");
        byte[] normalized;
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нужен файл PNG или JPEG");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg")) || reader.getWidth(0)>4096 || reader.getHeight(0)>4096)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PNG/JPEG, максимум 4096 × 4096");
                var source = reader.read(0);
                var image = new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int side = Math.min(source.getWidth(),source.getHeight());
                int x=(source.getWidth()-side)/2, y=(source.getHeight()-side)/2;
                g.drawImage(source,0,0,256,256,x,y,x+side,y+side,null); g.dispose();
                var output = new ByteArrayOutputStream(); ImageIO.write(image,"png",output); normalized=output.toByteArray();
            } finally { reader.dispose(); }
        } catch (javax.imageio.IIOException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать изображение"); }
        var user=current(authentication); user.setAvatar(normalized); users.save(user);
        return new Profile(user.getEmail(),user.getDisplayName(),true);
    }
}
