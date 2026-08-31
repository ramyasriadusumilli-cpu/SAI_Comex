package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §6 — a project. Second level of the hierarchy, below the group company
 * and above mining operations and shafts.
 *
 * <p>Associations are mapped as raw id columns rather than {@code @ManyToOne}
 * throughout this schema. Every list screen in the application is an aggregate
 * over the hierarchy, and object graphs turn those into N+1 storms; the joins
 * that matter are expressed explicitly in the repository projections instead.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "project_type", length = 60)
    private String projectType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "boundary_geojson", columnDefinition = "TEXT")
    private String boundaryGeojson;

    @Column(name = "project_manager_id")
    private Long projectManagerId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "planned_completion_date")
    private LocalDate plannedCompletionDate;

    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    /** PROPOSED | PLANNING | PROSPECTING | DEVELOPMENT | ACTIVE | SUSPENDED | COMPLETED | CLOSED */
    @Column(nullable = false, length = 30)
    private String status = "PROPOSED";

    @Column(name = "budget_amount", precision = 18, scale = 4)
    private BigDecimal budgetAmount;

    @Column(name = "budget_currency", length = 3)
    private String budgetCurrency;

    @Column(name = "licence_number", length = 80)
    private String licenceNumber;

    @Column(name = "licence_expiry_date")
    private LocalDate licenceExpiryDate;

    @Column(name = "permit_number", length = 80)
    private String permitNumber;

    @Column(name = "permit_expiry_date")
    private LocalDate permitExpiryDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
