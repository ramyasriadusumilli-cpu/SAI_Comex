package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRS §12 — one row per step of the settlement waterfall, in execution
 * order. Reading these rows top to bottom is the explanation of the
 * partner statement.
 */
@Entity
@Table(name = "settlement_calculations")
@Getter
@Setter
public class SettlementCalculation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    /** DEDUCTION | ALLOCATION | ADJUSTMENT | TOTAL */
    @Column(nullable = false, length = 20)
    private String stage;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_type", length = 40)
    private String ruleType;

    @Column(name = "rule_name", length = 200)
    private String ruleName;

    /** Human-readable arithmetic, e.g. "75,000.00 x 70.000000% = 52,500.00" */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Column(name = "input_amount", precision = 18, scale = 4)
    private BigDecimal inputAmount;

    @Column(name = "percent_applied", precision = 9, scale = 6)
    private BigDecimal percentApplied;

    @Column(name = "rate_applied", precision = 18, scale = 6)
    private BigDecimal rateApplied;

    @Column(name = "result_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal resultAmount;

    @Column(name = "running_balance", precision = 18, scale = 4)
    private BigDecimal runningBalance;

    /** SAICOMEX | PARTNER | NONE */
    @Column(length = 20)
    private String beneficiary;

    @Column(length = 3)
    private String currency;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
