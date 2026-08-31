package com.saicomex.entity;

import com.saicomex.common.AuditContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRS §15 — one row per shaft an expense is spread across. A DIRECT expense
 * still gets exactly one row here, so settlement queries never special-case
 * shared expenses.
 */
@Entity
@Table(name = "expense_allocations")
@Getter
@Setter
public class ExpenseAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id", nullable = false)
    private Long shaftId;

    @Column(name = "allocation_percent", precision = 9, scale = 6)
    private BigDecimal allocationPercent;

    @Column(name = "allocation_quantity", precision = 18, scale = 4)
    private BigDecimal allocationQuantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "base_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "basis_note", columnDefinition = "TEXT")
    private String basisNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 160, updatable = false)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (createdBy == null) createdBy = AuditContext.currentUser();
    }
}
