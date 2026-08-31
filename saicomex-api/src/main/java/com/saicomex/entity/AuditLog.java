package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SRS §39 — an audit trail entry for a critical action, with the old and new
 * values and the stated reason for the change.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "user_email", length = 160)
    private String userEmail;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_role", length = 40)
    private String userRole;

    /** CREATE | UPDATE | DELETE | APPROVE | REJECT | LOGIN | LOGOUT | EXPORT
     *  | ISSUE | RECEIVE | CALCULATE | PAY | READ */
    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** Human key: "Shaft 3", "Contract C-0007". */
    @Column(name = "entity_label", length = 200)
    private String entityLabel;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "field_name", length = 80)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }
}
