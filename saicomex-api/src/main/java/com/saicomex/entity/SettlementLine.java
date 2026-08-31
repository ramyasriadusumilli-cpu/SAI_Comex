package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §25 — one source row (sale, expense, production or manual adjustment)
 * a settlement was computed from.
 */
@Entity
@Table(name = "settlement_lines")
@Getter
@Setter
public class SettlementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    /** REVENUE | EXPENSE | PRODUCTION | ADJUSTMENT */
    @Column(name = "line_type", nullable = false, length = 30)
    private String lineType;

    /** sales | expenses | production_records | manual */
    @Column(name = "source_table", nullable = false, length = 40)
    private String sourceTable;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "line_date")
    private LocalDate lineDate;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "category_code", length = 40)
    private String categoryCode;

    @Column(precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_code", length = 20)
    private String unitCode;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "base_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal baseAmount;

    @Column(nullable = false)
    private Boolean included = true;

    @Column(name = "exclusion_reason", columnDefinition = "TEXT")
    private String exclusionReason;
}
