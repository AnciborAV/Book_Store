package ru.aav.profile;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;

/** HTTP-адаптер профиля; не принимает идентификатор чужого пользователя. */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profiles;
    private final AvatarProcessor avatars;

    @GetMapping
    public ProfileService.ProfileDto profile(Principal principal) {
        return profiles.profile(principal.getName());
    }

    @GetMapping("/avatar")
    public ResponseEntity<byte[]> avatar(Principal principal) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore()).body(profiles.avatar(principal.getName()));
    }

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileService.ProfileDto upload(Principal principal, @RequestParam("file") MultipartFile file) {
        return profiles.changeAvatar(principal.getName(), avatars.process(file));
    }
}
