package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §19 — a purchase order. Carries full created/updated/deleted audit
 * columns, so it extends {@link SoftDeletableEntity}. Lines live in
 * {@link PurchaseOrderLine}; totals are maintained by the service.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
public class PurchaseOrder extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "po_number", nullable = false, length = 50, unique = true)
    private String poNumber;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(nullable = false, length = 3, columnDefinition = "char")
    private String currency;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // DRAFT | SUBMITTED | APPROVED | PARTIALLY_RECEIVED | RECEIVED | CANCELLED
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
