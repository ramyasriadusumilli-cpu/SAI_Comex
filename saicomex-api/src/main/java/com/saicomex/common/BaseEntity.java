package com.saicomex.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Audit columns every business table carries (SRS §39).
 *
 * <p>{@code createdBy} / {@code updatedBy} hold the acting user's email rather
 * than a FK: an audit record has to survive the user row being deactivated or
 * renamed, and a settlement printed in 2026 must still say who approved it in
 * 2026 even if that person has since left.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 160, updatable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 160)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (createdBy == null) createdBy = AuditContext.currentUser();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        updatedBy = AuditContext.currentUser();
    }
}
