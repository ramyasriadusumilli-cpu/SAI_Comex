package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §13, §14 — a single production entry (date + project + operation +
 * shaft). Corrections never overwrite: a correction is a new row pointing
 * back via {@code correctsRecordId}, and the original moves to CORRECTED.
 */
@Entity
@Table(name = "production_records")
@Getter
@Setter
public class ProductionRecord extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id", nullable = false)
    private Long shaftId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    /** DAY | NIGHT | MORNING | AFTERNOON */
    @Column(length = 20)
    private String shift;

    /** DAILY | SHIFT | WEEKLY | MONTHLY */
    @Column(name = "period_type", nullable = false, length = 20)
    private String periodType = "DAILY";

    @Column(name = "ore_tonnes", precision = 18, scale = 4)
    private BigDecimal oreTonnes;

    /** g/t */
    @Column(precision = 12, scale = 6)
    private BigDecimal grade;

    @Column(name = "recovery_percent", precision = 9, scale = 4)
    private BigDecimal recoveryPercent;

    @Column(name = "gold_recovered", precision = 18, scale = 4)
    private BigDecimal goldRecovered;

    /** Headline production figure. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(name = "processing_output", precision = 18, scale = 4)
    private BigDecimal processingOutput;

    @Column(name = "target_quantity", precision = 18, scale = 4)
    private BigDecimal targetQuantity;

    @Column(name = "variance_quantity", precision = 18, scale = 4)
    private BigDecimal varianceQuantity;

    /** DRAFT | SUBMITTED | VERIFIED | APPROVED | REJECTED | CORRECTED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "recorded_by_user_id")
    private Long recordedByUserId;

    @Column(name = "verified_by_user_id")
    private Long verifiedByUserId;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "corrects_record_id")
    private Long correctsRecordId;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    /** WEB | MOBILE | IMPORT */
    @Column(nullable = false, length = 20)
    private String source = "WEB";

    /** Idempotency key for offline mobile sync (SRS §33). */
    @Column(name = "client_uuid", length = 64)
    private String clientUuid;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
