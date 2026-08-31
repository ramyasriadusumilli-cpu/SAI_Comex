package com.saicomex.entity;

import com.saicomex.common.AuditContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §24 — append-only financial ledger. Every revenue, expense, payment
 * and settlement writes one row here pointing back to the record that
 * caused it, so any number can be traced to its source in a single query.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** REVENUE | EXPENSE | PAYMENT | SETTLEMENT | CAPEX | INVENTORY | ADJUSTMENT */
    @Column(name = "entry_type", nullable = false, length = 30)
    private String entryType;

    /** DEBIT | CREDIT */
    @Column(nullable = false, length = 10)
    private String direction;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "base_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal baseAmount;

    @Column(name = "source_table", nullable = false, length = 40)
    private String sourceTable;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "reversal_of_id")
    private Long reversalOfId;

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
