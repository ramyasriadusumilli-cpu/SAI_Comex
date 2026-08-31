package com.saicomex.entity;

import com.saicomex.common.AuditContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRS §17 — a fuel movement. A fuel issue produces three linked rows at once:
 * this record, the {@code inventory_transaction} that moved the stock, and the
 * {@code expense} that captured the cost. Append-only (created/deleted, no
 * updated), so it is a plain entity like {@link InventoryTransaction}.
 */
@Entity
@Table(name = "fuel_transactions")
@Getter
@Setter
public class FuelTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "inventory_transaction_id")
    private Long inventoryTransactionId;

    @Column(name = "expense_id")
    private Long expenseId;

    // PURCHASE | ISSUE | TRANSFER | ADJUSTMENT
    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    // DIESEL | PETROL | OIL
    @Column(name = "fuel_type", nullable = false, length = 20)
    private String fuelType;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "quantity_litres", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantityLitres;

    @Column(name = "unit_cost", precision = 18, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 18, scale = 4)
    private BigDecimal totalCost;

    @Column(length = 3, columnDefinition = "char")
    private String currency;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "recipient_employee_id")
    private Long recipientEmployeeId;

    @Column(name = "recipient_name", length = 160)
    private String recipientName;

    @Column(name = "odometer_reading", precision = 12, scale = 2)
    private BigDecimal odometerReading;

    @Column(name = "hour_meter_reading", precision = 12, scale = 2)
    private BigDecimal hourMeterReading;

    @Column(name = "opening_stock", precision = 18, scale = 4)
    private BigDecimal openingStock;

    @Column(name = "closing_stock", precision = 18, scale = 4)
    private BigDecimal closingStock;

    @Column(length = 120)
    private String reference;

    @Column(nullable = false, length = 20)
    private String source = "WEB";

    @Column(name = "client_uuid", length = 64)
    private String clientUuid;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 160, updatable = false)
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
