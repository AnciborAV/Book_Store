package ru.aav.auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth/password")
public class PasswordRecoveryController {
    private final PasswordRecovery recovery;
    public PasswordRecoveryController(PasswordRecovery recovery) { this.recovery = recovery; }
    public record Request(@NotBlank @Email @Size(max=255) String email) {}
    public record Reset(@NotBlank @Size(max=100) String token,
        @NotBlank @Size(min=8,max=72) String password,
        @NotBlank @Size(min=8,max=72) String confirmation) {}
    @PostMapping("/request") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@Valid @RequestBody Request input) { recovery.request(input.email()); }
    @PostMapping("/reset") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody Reset input, HttpServletRequest request) {
        recovery.reset(input.token(), input.password(), input.confirmation());
        var session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
