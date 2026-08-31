package com.saicomex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** SRS §38 — authentication payloads. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    /**
     * @param permissions module.action codes, used by the SPA to decide what to
     *                    render. Never the authority for what the caller may do —
     *                    that is re-checked server-side on every request.
     */
    public record LoginResponse(
            String token,
            long expiresInMs,
            Long userId,
            String email,
            String fullName,
            String roleCode,
            String roleName,
            List<String> permissions,
            List<Long> projectIds,
            List<Long> shaftIds,
            boolean mustChangePassword,
            String preferredCurrency
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, message = "Password must be at least 10 characters")
            String newPassword
    ) {}

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, message = "Password must be at least 10 characters")
            String newPassword
    ) {}

    /** GET /api/auth/me — everything the shell needs to render itself. */
    public record CurrentUser(
            Long userId,
            String email,
            String fullName,
            String roleCode,
            String roleName,
            List<String> permissions,
            List<Long> projectIds,
            List<Long> shaftIds,
            boolean mustChangePassword,
            String preferredCurrency,
            String companyName,
            String reportingCurrency
    ) {}
}
