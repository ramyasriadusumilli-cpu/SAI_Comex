package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §7 — a mining operation, one level below a project and above shafts.
 * A shaft may hang directly off a project with no operation layer, which is
 * why {@code shafts.mining_operation_id} is nullable.
 */
@Entity
@Table(name = "mining_operations")
@Getter
@Setter
public class MiningOperation extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** SHAFT_MINING | ALLUVIAL | RIVER | PROCESSING | MILLING | OTHER */
    @Column(name = "operation_type", nullable = false, length = 60)
    private String operationType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_id")
    private Long locationId;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "manager_id")
    private Long managerId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** PROPOSED | DEVELOPMENT | ACTIVE | SUSPENDED | CLOSED */
    @Column(nullable = false, length = 30)
    private String status = "PROPOSED";

    @Column(name = "budget_amount", precision = 18, scale = 4)
    private BigDecimal budgetAmount;

    @Column(name = "budget_currency", length = 3)
    private String budgetCurrency;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
