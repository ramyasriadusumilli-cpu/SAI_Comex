package com.saicomex.entity;

import com.saicomex.common.AuditContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRS §19 — one stock movement. Append-only: a transaction is never edited,
 * only soft-deleted (reversed), so this table has {@code created_*}/{@code
 * deleted_*} but deliberately no {@code updated_*} columns — hence a plain
 * entity rather than {@code SoftDeletableEntity}, which would map an
 * {@code updated_at} column that does not exist.
 */
@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "transaction_number", nullable = false, length = 50, unique = true)
    private String transactionNumber;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    // RECEIPT | ISSUE | TRANSFER_OUT | TRANSFER_IN | ADJUSTMENT | COUNT | RETURN
    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    // Signed: + in, − out.
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 18, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 18, scale = 4)
    private BigDecimal totalCost;

    @Column(length = 3, columnDefinition = "char")
    private String currency;

    @Column(name = "balance_after", precision = 18, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "recipient_employee_id")
    private Long recipientEmployeeId;

    @Column(name = "recipient_name", length = 160)
    private String recipientName;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "transfer_store_id")
    private Long transferStoreId;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "permit_reference", length = 80)
    private String permitReference;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(length = 120)
    private String reference;

    @Column(nullable = false, length = 20)
    private String source = "WEB";

    @Column(name = "client_uuid", length = 64)
    private String clientUuid;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 160, updatable = false)
    private String createdBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 160)
    private String deletedBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (createdBy == null) createdBy = AuditContext.currentUser();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete(String actor) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = actor;
    }
}
