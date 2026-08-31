package com.saicomex.entity;

import com.saicomex.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §31 — an alert threshold, stored as a row rather than a constant so it
 * can be tuned without a redeploy.
 */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
public class AlertRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 60, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** PRODUCTION | EXPENSE | CONTRACT | INVENTORY | EQUIPMENT | REPORTING | FINANCIAL */
    @Column(nullable = false, length = 30)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** INFO | WARNING | CRITICAL */
    @Column(nullable = false, length = 20)
    private String severity = "WARNING";

    /** NULL project/shaft = applies group-wide. */
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "shaft_id")
    private Long shaftId;

    /** LESS_THAN | GREATER_THAN | PERCENT_BELOW | PERCENT_ABOVE | NO_ACTIVITY_DAYS | DAYS_BEFORE */
    @Column(nullable = false, length = 20)
    private String comparison = "LESS_THAN";

    @Column(name = "threshold_value", precision = 18, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_unit", length = 20)
    private String thresholdUnit;

    @Column(name = "evaluation_window_days", nullable = false)
    private Integer evaluationWindowDays = 1;

    /** Comma-separated role codes. */
    @Column(name = "notify_roles", length = 300)
    private String notifyRoles;

    @Column(name = "notify_emails", length = 500)
    private String notifyEmails;

    /** IN_APP,EMAIL,PUSH */
    @Column(nullable = false, length = 120)
    private String channels = "IN_APP";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
