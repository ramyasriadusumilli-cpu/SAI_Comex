package com.saicomex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SRS §37. Request and response shapes for roles and the permission matrix.
 */
public final class RoleDtos {

    private RoleDtos() {}

    /** Create / update payload. {@code permissionCodes} is the whole grant set, not a delta. */
    public record RoleRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 80) String name,
            String description,
            @NotNull Boolean isActive,
            @NotNull Integer displayOrder,
            List<String> permissionCodes
    ) {}

    /** Row shape for the roles list. */
    public record RoleSummary(
            Long id,
            String code,
            String name,
            String description,
            Boolean isSystem,
            Boolean isActive,
            int userCount,
            int permissionCount
    ) {}

    /** Full record for the role detail / permission-editing page. */
    public record RoleDetail(
            Long id,
            String code,
            String name,
            String description,
            Boolean isSystem,
            Boolean isActive,
            int userCount,
            int permissionCount,
            Integer displayOrder,
            List<String> permissionCodes,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime updatedAt,
            String updatedBy
    ) {}

    /** One grantable permission. */
    public record PermissionDto(
            String code,
            String module,
            String action,
            String description
    ) {}

    /** The permission catalogue, grouped by module, for the permission matrix UI. */
    public record ModulePermissions(
            String module,
            List<PermissionDto> permissions
    ) {}
}
