package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §32, §48 — a daily or weekly site report.
 */
@Entity
@Table(name = "daily_reports")
@Getter
@Setter
public class DailyReport extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "report_number", nullable = false, length = 50, unique = true)
    private String reportNumber;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(length = 20)
    private String shift;

    @Column(name = "reported_by_user_id")
    private Long reportedByUserId;

    @Column
    private Integer headcount;

    @Column(name = "hours_worked", precision = 10, scale = 2)
    private BigDecimal hoursWorked;

    @Column(name = "production_quantity", precision = 18, scale = 4)
    private BigDecimal productionQuantity;

    @Column(name = "production_unit", length = 20)
    private String productionUnit;

    @Column(name = "ore_tonnes", precision = 18, scale = 4)
    private BigDecimal oreTonnes;

    @Column(name = "diesel_used_litres", precision = 18, scale = 4)
    private BigDecimal dieselUsedLitres;

    @Column(name = "explosives_used", precision = 18, scale = 4)
    private BigDecimal explosivesUsed;

    @Column(name = "equipment_hours", precision = 12, scale = 2)
    private BigDecimal equipmentHours;

    @Column(name = "downtime_hours", precision = 12, scale = 2)
    private BigDecimal downtimeHours;

    @Column(length = 80)
    private String weather;

    @Column(name = "safety_incidents", nullable = false)
    private Integer safetyIncidents = 0;

    @Column(name = "incident_notes", columnDefinition = "TEXT")
    private String incidentNotes;

    @Column(columnDefinition = "TEXT")
    private String activities;

    @Column(columnDefinition = "TEXT")
    private String issues;

    @Column(name = "plan_next_shift", columnDefinition = "TEXT")
    private String planNextShift;

    /** DRAFT | SUBMITTED | VERIFIED | APPROVED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "verified_by_user_id")
    private Long verifiedByUserId;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /** WEB | MOBILE | IMPORT */
    @Column(nullable = false, length = 20)
    private String source = "WEB";

    @Column(name = "client_uuid", length = 64)
    private String clientUuid;
}
