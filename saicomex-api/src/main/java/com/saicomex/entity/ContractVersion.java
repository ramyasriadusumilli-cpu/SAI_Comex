package com.saicomex.entity;

import com.saicomex.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §10 — contract versioning. An amendment creates a new version row and
 * a new commercial agreement; the previous pair stays intact so a historical
 * settlement can always be recomputed exactly as it was.
 */
@Entity
@Table(name = "contract_versions")
@Getter
@Setter
public class ContractVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "change_reason", nullable = false, columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary;

    /** DRAFT | PENDING_APPROVAL | ACTIVE | SUPERSEDED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
