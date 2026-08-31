package com.saicomex.entity;

import com.saicomex.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §11 — a configurable agreement parameter (split, deduction, fee or
 * recovery). Every business rule the settlement engine applies is a row
 * here rather than a hard-coded percentage (SRS §60).
 */
@Entity
@Table(name = "agreement_rules")
@Getter
@Setter
public class AgreementRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agreement_id", nullable = false)
    private Long agreementId;

    @Column(name = "rule_type", nullable = false, length = 40)
    private String ruleType;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Order of application within the waterfall. Lower runs first. */
    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo = 100;

    /** ALL | EXPENSE_CATEGORY | PRODUCT | COST_TYPE */
    @Column(nullable = false, length = 30)
    private String scope = "ALL";

    /** FK added in V4 once expense_categories exists. */
    @Column(name = "expense_category_id")
    private Long expenseCategoryId;

    @Column(name = "scope_value", length = 80)
    private String scopeValue;

    /** PERCENTAGE | FIXED_AMOUNT | RATE_PER_UNIT | TIERED | FULL_AMOUNT */
    @Column(name = "calculation_method", nullable = false, length = 30)
    private String calculationMethod = "PERCENTAGE";

    @Column(name = "saicomex_percent", precision = 9, scale = 6)
    private BigDecimal saicomexPercent;

    @Column(name = "partner_percent", precision = 9, scale = 6)
    private BigDecimal partnerPercent;

    @Column(name = "fixed_amount", precision = 18, scale = 4)
    private BigDecimal fixedAmount;

    @Column(name = "rate_amount", precision = 18, scale = 6)
    private BigDecimal rateAmount;

    /** per gram / tonne / litre / day */
    @Column(name = "rate_unit", length = 20)
    private String rateUnit;

    @Column(length = 3)
    private String currency;

    /** SAICOMEX | PARTNER | SHARED — who carries this cost when it is a cost-share rule. */
    @Column(name = "borne_by", nullable = false, length = 20)
    private String borneBy = "SHARED";

    /** Deduction rules only: taken off the gross before allocation. */
    @Column(name = "deduct_before_split", nullable = false)
    private Boolean deductBeforeSplit = false;

    @Column(name = "min_amount", precision = 18, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 18, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "cap_percent", precision = 9, scale = 6)
    private BigDecimal capPercent;

    /** Capital recovery: recover {@code fixedAmount} at {@code rateAmount} per period. */
    @Column(name = "recoverable_total", precision = 18, scale = 4)
    private BigDecimal recoverableTotal;

    @Column(name = "recovered_to_date", nullable = false, precision = 18, scale = 4)
    private BigDecimal recoveredToDate = BigDecimal.ZERO;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
