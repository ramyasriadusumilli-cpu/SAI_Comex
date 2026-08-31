package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §25 — one settlement = one partner, one shaft, one period, computed
 * from the contract that was ACTIVE in that period. {@code agreementId} pins
 * the exact agreement version used, so a re-opened statement never
 * recomputes against a later amendment.
 */
@Entity
@Table(name = "settlements")
@Getter
@Setter
public class Settlement extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "settlement_number", nullable = false, length = 50, unique = true)
    private String settlementNumber;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id", nullable = false)
    private Long shaftId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "agreement_id", nullable = false)
    private Long agreementId;

    @Column(name = "contract_version_id")
    private Long contractVersionId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    /** The SRS §12 waterfall, materialised. */
    @Column(name = "gross_revenue", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossRevenue = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_distributable", nullable = false, precision = 18, scale = 4)
    private BigDecimal netDistributable = BigDecimal.ZERO;

    @Column(name = "saicomex_share", nullable = false, precision = 18, scale = 4)
    private BigDecimal saicomexShare = BigDecimal.ZERO;

    @Column(name = "partner_share", nullable = false, precision = 18, scale = 4)
    private BigDecimal partnerShare = BigDecimal.ZERO;

    @Column(name = "partner_adjustments", nullable = false, precision = 18, scale = 4)
    private BigDecimal partnerAdjustments = BigDecimal.ZERO;

    @Column(name = "partner_net_payable", nullable = false, precision = 18, scale = 4)
    private BigDecimal partnerNetPayable = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "amount_retained", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountRetained = BigDecimal.ZERO;

    @Column(name = "amount_outstanding", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountOutstanding = BigDecimal.ZERO;

    @Column(name = "total_production", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalProduction = BigDecimal.ZERO;

    @Column(name = "production_unit", length = 20)
    private String productionUnit;

    @Column(name = "total_expenses", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalExpenses = BigDecimal.ZERO;

    /** DRAFT | CALCULATED | PENDING_APPROVAL | APPROVED | PARTIALLY_PAID | PAID | CANCELLED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "calculated_by", length = 160)
    private String calculatedBy;

    /** Hash of the inputs consumed; a changed hash flags a stale statement. */
    @Column(name = "calculation_hash", length = 64)
    private String calculationHash;

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
