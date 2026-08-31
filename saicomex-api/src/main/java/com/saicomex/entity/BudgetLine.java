package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §26 — one budget line per category. Actuals are computed from
 * {@link LedgerEntry} on read, never stored here — a cached actual is a
 * cached lie the first time an expense is back-dated.
 */
@Entity
@Table(name = "budget_lines")
@Getter
@Setter
public class BudgetLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "budget_id", nullable = false)
    private Long budgetId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(length = 300)
    private String description;

    @Column(name = "budgeted_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal budgetedAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
