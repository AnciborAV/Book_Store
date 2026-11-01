package ru.aav.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Registration(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 80) String displayName,
    @NotBlank @Size(min = 8, max = 72) String password
) {}
