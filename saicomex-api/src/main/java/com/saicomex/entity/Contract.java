package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §10 — a contract. A shaft may have several over its lifecycle; exactly
 * one may be ACTIVE at any instant (enforced by {@code uq_contract_active_per_shaft}).
 */
@Entity
@Table(name = "contracts")
@Getter
@Setter
public class Contract extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_number", nullable = false, length = 50, unique = true)
    private String contractNumber;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "contract_type_id")
    private Long contractTypeId;

    @Column(length = 200)
    private String title;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(name = "signed_date")
    private LocalDate signedDate;

    /** DRAFT | PENDING_APPROVAL | APPROVED | ACTIVE | EXPIRED | TERMINATED | SUPERSEDED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    @Column(name = "settlement_currency", nullable = false, length = 3)
    private String settlementCurrency = "USD";

    /** WEEKLY | FORTNIGHTLY | MONTHLY | PER_SALE */
    @Column(name = "settlement_frequency", nullable = false, length = 20)
    private String settlementFrequency = "MONTHLY";

    @Column(name = "special_conditions", columnDefinition = "TEXT")
    private String specialConditions;

    @Column(name = "termination_notes", columnDefinition = "TEXT")
    private String terminationNotes;

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
