package com.veritaspay.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Size(min = 8, max = 100) String password,
            Role role
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record UserProfile(
            UUID id,
            String email,
            String fullName,
            Role role
    ) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            long expiresInMs,
            UserProfile user
    ) {}
}
