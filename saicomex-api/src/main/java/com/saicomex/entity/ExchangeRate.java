package com.saicomex.entity;

import com.saicomex.common.AuditContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §40 — a currency-pair rate on a given day. Historical figures are
 * converted using the rate that was current at the time, so this table is
 * append-only: rows are never updated once created, only superseded by a
 * later {@code effective_date}. It carries {@code created_at}/{@code
 * created_by} but no {@code updated_at}/{@code updated_by} or soft delete, so
 * it extends neither {@link com.saicomex.common.BaseEntity} nor {@link
 * com.saicomex.common.SoftDeletableEntity}.
 */
@Entity
@Table(name = "exchange_rates")
@Getter
@Setter
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(length = 60)
    private String source;

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
