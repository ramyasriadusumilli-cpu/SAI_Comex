package com.saicomex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SRS §36. Request and response shapes for platform users, including the
 * project/shaft data-scoping assignments carried alongside each account.
 */
public final class UserDtos {

    private UserDtos() {}

    /** Create / update payload. Password is never accepted here — see {@code CreatedUser} and reset-password. */
    public record UserRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Size(max = 40) String phone,
            @Size(max = 120) String jobTitle,
            @Size(max = 120) String department,
            @NotNull Long roleId,
            String status,
            @Size(max = 3) String preferredCurrency,
            List<Long> projectIds,
            List<Long> shaftIds
    ) {}

    /** Row shape for the users list. */
    public record UserSummary(
            Long id,
            String email,
            String fullName,
            String roleCode,
            String roleName,
            String department,
            String status,
            LocalDateTime lastLoginAt,
            int assignedProjectCount,
            int assignedShaftCount
    ) {}

    /** Full record for the user detail page. */
    public record UserDetail(
            Long id,
            String email,
            String fullName,
            String roleCode,
            String roleName,
            String department,
            String status,
            LocalDateTime lastLoginAt,
            int assignedProjectCount,
            int assignedShaftCount,
            String jobTitle,
            String phone,
            String preferredCurrency,
            Boolean mfaEnabled,
            Boolean mustChangePassword,
            List<Long> projectIds,
            List<Long> shaftIds,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime updatedAt,
            String updatedBy
    ) {}

    /**
     * Response of {@code POST /api/users}. The generated initial password is
     * returned exactly once — it is never stored in plain text and never
     * appears on {@link UserDetail}, so this is the operator's only chance to
     * see or hand it over.
     */
    public record CreatedUser(UserDetail user, String initialPassword) {}

    /** Payload for {@code POST /{id}/reset-password}. */
    public record PasswordResetRequest(
            @NotBlank @Size(min = 8, max = 100) String newPassword
    ) {}

    /** Payload for {@code PATCH /{id}/status}. */
    public record UserStatusRequest(
            @NotBlank String status,
            String reason
    ) {}
}
