package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §22 — a maintenance job against a piece of equipment. Costs roll up from
 * {@link MaintenancePart} plus labour and other. Full audit + soft delete.
 */
@Entity
@Table(name = "maintenance_records")
@Getter
@Setter
public class MaintenanceRecord extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "job_number", nullable = false, length = 50, unique = true)
    private String jobNumber;

    // PREVENTIVE | CORRECTIVE | INSPECTION | OVERHAUL
    @Column(name = "maintenance_type", nullable = false, length = 30)
    private String maintenanceType;

    @Column(nullable = false, length = 20)
    private String priority = "NORMAL";

    @Column(name = "reported_date")
    private LocalDate reportedDate;

    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @Column(name = "next_service_hours", precision = 12, scale = 2)
    private BigDecimal nextServiceHours;

    @Column(name = "hour_meter_reading", precision = 12, scale = 2)
    private BigDecimal hourMeterReading;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "work_performed", columnDefinition = "TEXT")
    private String workPerformed;

    @Column(name = "technician_name", length = 160)
    private String technicianName;

    @Column(name = "technician_employee_id")
    private Long technicianEmployeeId;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "parts_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal partsCost = BigDecimal.ZERO;

    @Column(name = "labour_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal labourCost = BigDecimal.ZERO;

    @Column(name = "other_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal otherCost = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(length = 3, columnDefinition = "char")
    private String currency;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "downtime_hours", nullable = false, precision = 12, scale = 2)
    private BigDecimal downtimeHours = BigDecimal.ZERO;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "shaft_id")
    private Long shaftId;

    // OPEN | IN_PROGRESS | AWAITING_PARTS | COMPLETED | CANCELLED
    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(columnDefinition = "TEXT")
    private String notes;
}
