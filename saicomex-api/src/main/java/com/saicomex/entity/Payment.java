package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §27 — a payment to a partner, supplier, employee or contractor.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "payment_number", nullable = false, length = 50, unique = true)
    private String paymentNumber;

    /** PARTNER | SUPPLIER | EMPLOYEE | CONTRACTOR | OTHER */
    @Column(name = "payment_type", nullable = false, length = 30)
    private String paymentType;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "base_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal baseAmount;

    /** EFT | CASH | MOBILE | CHEQUE | TRANSFER */
    @Column(name = "payment_method", nullable = false, length = 40)
    private String paymentMethod;

    @Column(name = "bank_reference", length = 120)
    private String bankReference;

    @Column(length = 120)
    private String reference;

    /** DRAFT | PENDING_APPROVAL | APPROVED | PAID | REJECTED | CANCELLED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
