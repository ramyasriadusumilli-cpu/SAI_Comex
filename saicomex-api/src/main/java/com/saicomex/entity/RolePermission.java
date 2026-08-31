package com.saicomex.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §37 — join row granting a {@link Permission} to a {@link Role}.
 */
@Entity
@Table(name = "role_permissions")
@Getter
@Setter
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;
}
