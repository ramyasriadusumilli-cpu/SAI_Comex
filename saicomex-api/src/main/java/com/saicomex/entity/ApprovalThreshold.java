package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §16 — configurable approval threshold, matched by entity type,
 * expense class and amount band ("These thresholds must be configurable").
 */
@Entity
@Table(name = "approval_thresholds")
@Getter
@Setter
public class ApprovalThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType = "EXPENSE";

    /** NULL = group default. */
    @Column(name = "project_id")
    private Long projectId;

    /** NULL = any. */
    @Column(name = "expense_class", length = 20)
    private String expenseClass;

    @Column(name = "min_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal minAmount = BigDecimal.ZERO;

    /** NULL = no upper bound. */
    @Column(name = "max_amount", precision = 18, scale = 4)
    private BigDecimal maxAmount;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "step_no", nullable = false)
    private Integer stepNo = 1;

    @Column(name = "step_name", nullable = false, length = 80)
    private String stepName;

    @Column(name = "required_role", nullable = false, length = 40)
    private String requiredRole;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
