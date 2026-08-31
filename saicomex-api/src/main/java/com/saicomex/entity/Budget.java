package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §26 — a budget at group, project, operation or shaft level.
 */
@Entity
@Table(name = "budgets")
@Getter
@Setter
public class Budget extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 200)
    private String name;

    /** GROUP | PROJECT | OPERATION | SHAFT */
    @Column(name = "budget_level", nullable = false, length = 20)
    private String budgetLevel;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    /** ANNUAL | QUARTERLY | MONTHLY */
    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType = "ANNUAL";

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** DRAFT | PENDING_APPROVAL | APPROVED | ACTIVE | CLOSED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
