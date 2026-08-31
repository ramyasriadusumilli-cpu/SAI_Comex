package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §23 — a sale of production. Carries the full hierarchy for revenue
 * attribution and links to the settlement it was consumed by, if any.
 */
@Entity
@Table(name = "sales")
@Getter
@Setter
public class Sale extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "sale_number", nullable = false, length = 50, unique = true)
    private String saleNumber;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(nullable = false, length = 80)
    private String product = "GOLD";

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(precision = 12, scale = 6)
    private BigDecimal grade;

    @Column(name = "assay_reference", length = 80)
    private String assayReference;

    @Column(name = "assay_percent", precision = 9, scale = 4)
    private BigDecimal assayPercent;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 6)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "deductions_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal deductionsAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "royalty_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal royaltyAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "gross_base_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossBaseAmount;

    @Column(name = "net_base_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netBaseAmount;

    /** UNPAID | PARTIAL | PAID */
    @Column(name = "payment_status", nullable = false, length = 20)
    private String paymentStatus = "UNPAID";

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "amount_received", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountReceived = BigDecimal.ZERO;

    /** Has this revenue already been split with the partner? UNSETTLED | SETTLED */
    @Column(name = "settlement_status", nullable = false, length = 20)
    private String settlementStatus = "UNSETTLED";

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "invoice_number", length = 80)
    private String invoiceNumber;

    @Column(length = 120)
    private String reference;

    /** DRAFT | CONFIRMED | CANCELLED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(columnDefinition = "TEXT")
    private String notes;
}
