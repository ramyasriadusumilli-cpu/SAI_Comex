package com.saicomex.entity;

import com.saicomex.common.AuditContext;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §21 — one placement of a piece of equipment. Append-only history: a
 * {@code toDate} of NULL marks the current placement, and the
 * {@code uq_equip_alloc_current} index guarantees at most one open row per
 * equipment. Re-allocating closes the open row and opens a new one; the row is
 * never edited in place. Created/created_by only — no updated/deleted columns.
 */
@Entity
@Table(name = "equipment_allocations")
@Getter
@Setter
public class EquipmentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Column(name = "operator_employee_id")
    private Long operatorEmployeeId;

    @Column(name = "opening_hours", precision = 12, scale = 2)
    private BigDecimal openingHours;

    @Column(name = "closing_hours", precision = 12, scale = 2)
    private BigDecimal closingHours;

    @Column(name = "hire_rate", precision = 18, scale = 4)
    private BigDecimal hireRate;

    @Column(name = "hire_rate_unit", length = 20)
    private String hireRateUnit;

    @Column(name = "rate_currency", length = 3, columnDefinition = "char")
    private String rateCurrency;

    @Column(columnDefinition = "TEXT")
    private String reason;

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
