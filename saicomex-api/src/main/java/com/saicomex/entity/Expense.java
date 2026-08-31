package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §15, §16 — an expense, direct or shared. A NULL {@code shaftId} marks a
 * shared expense; its split across shafts lives in {@link ExpenseAllocation}.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
public class Expense extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "expense_number", nullable = false, length = 50, unique = true)
    private String expenseNumber;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(length = 20)
    private String unit;

    @Column(name = "unit_cost", precision = 18, scale = 6)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /** In the group reporting currency. */
    @Column(name = "base_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    /** DIRECT | MANUAL | PERCENTAGE | QUANTITY | EQUAL | COST_DRIVER */
    @Column(name = "allocation_method", nullable = false, length = 30)
    private String allocationMethod = "DIRECT";

    @Column(name = "is_shared", nullable = false)
    private Boolean isShared = false;

    @Column(length = 120)
    private String reference;

    @Column(name = "invoice_number", length = 80)
    private String invoiceNumber;

    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    /** DRAFT | SUBMITTED | PENDING_APPROVAL | APPROVED | REJECTED | PAID | CANCELLED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approval_stage", length = 40)
    private String approvalStage;

    @Column(name = "submitted_by_user_id")
    private Long submittedByUserId;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** WEB | MOBILE | IMPORT */
    @Column(nullable = false, length = 20)
    private String source = "WEB";

    @Column(name = "client_uuid", length = 64)
    private String clientUuid;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
