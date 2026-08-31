package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRS §31 — a raised alert instance, evaluated from an {@link AlertRule} (or
 * raised ad hoc with a null rule).
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "alert_rule_id")
    private Long alertRuleId;

    @Column(nullable = false, length = 30)
    private String category;

    /** INFO | WARNING | CRITICAL */
    @Column(nullable = false, length = 20)
    private String severity = "WARNING";

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "actual_value", precision = 18, scale = 4)
    private BigDecimal actualValue;

    @Column(name = "threshold_value", precision = 18, scale = 4)
    private BigDecimal thresholdValue;

    /** OPEN | ACKNOWLEDGED | RESOLVED | DISMISSED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "acknowledged_by", length = 160)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    /** Stops the nightly evaluator re-raising the same open alert every run. */
    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @PrePersist
    void onCreate() {
        if (triggeredAt == null) triggeredAt = LocalDateTime.now();
    }
}
