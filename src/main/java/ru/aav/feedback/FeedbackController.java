package ru.aav.feedback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static ru.aav.feedback.FeedbackService.*;

@RestController
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService service;
    public record Submission(@NotNull UUID requestId, @NotNull Kind kind,
        @NotBlank @Size(max=3000) String body, boolean publicConsent) {}
    public record Moderation(@Min(0) int version, @NotNull State state) {}

    @GetMapping("/api/site-feedback")
    public Page published(@RequestParam(defaultValue="0") int page) {
        return service.list(page, false, State.PUBLISHED);
    }
    @PostMapping("/api/site-feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> submit(Principal principal, @Valid @RequestBody Submission input) {
        return Map.of("id", service.create(principal.getName(), input.requestId(), input.kind(), input.body(), input.publicConsent()));
    }
    @GetMapping("/api/admin/site-feedback")
    public Page inbox(@RequestParam(defaultValue="0") int page,
                      @RequestParam(defaultValue="PENDING") State state) {
        return service.list(page, true, state);
    }
    @PatchMapping("/api/admin/site-feedback/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moderate(@PathVariable UUID id, @Valid @RequestBody Moderation input) {
        service.moderate(id, input.version(), input.state());
    }
}
