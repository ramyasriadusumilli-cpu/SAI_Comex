package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §8 — a shaft, the primary operational entity. Every shaft carries its
 * own financial and operational account.
 */
@Entity
@Table(name = "shafts")
@Getter
@Setter
public class Shaft extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "shaft_number", length = 40)
    private String shaftNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_id")
    private Long locationId;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * Denormalised from the active contract for fast list screens; the
     * contract remains the authority.
     */
    @Column(name = "owner_partner_id")
    private Long ownerPartnerId;

    @Column(name = "shaft_manager_id")
    private Long shaftManagerId;

    @Column(name = "depth_metres", precision = 10, scale = 2)
    private BigDecimal depthMetres;

    @Column(name = "commissioned_date")
    private LocalDate commissionedDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    /**
     * PROPOSED | CONTRACT_PENDING | CONTRACTED | MOBILISATION | DEVELOPMENT
     * | ACTIVE | TEMPORARILY_STOPPED | SUSPENDED | CLOSED
     */
    @Column(nullable = false, length = 30)
    private String status = "PROPOSED";

    @Column(name = "production_target", precision = 18, scale = 4)
    private BigDecimal productionTarget;

    @Column(name = "production_target_unit", length = 20)
    private String productionTargetUnit;

    /** DAILY | WEEKLY | MONTHLY */
    @Column(name = "production_target_period", length = 20)
    private String productionTargetPeriod;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
