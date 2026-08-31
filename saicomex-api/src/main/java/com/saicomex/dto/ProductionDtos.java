package com.saicomex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** SRS §13, §14 — production capture, verification and correction. */
public final class ProductionDtos {

    private ProductionDtos() {}

    /** Create / update payload. */
    public record ProductionRequest(
            @NotNull Long projectId,
            Long miningOperationId,
            @NotNull Long shaftId,
            @NotNull LocalDate productionDate,
            String shift,
            String periodType,
            BigDecimal oreTonnes,
            BigDecimal grade,
            BigDecimal recoveryPercent,
            BigDecimal goldRecovered,
            @NotNull @PositiveOrZero BigDecimal quantity,
            @NotBlank String unitCode,
            BigDecimal processingOutput,
            BigDecimal targetQuantity,
            String notes,
            /** SRS §33 — offline sync idempotency key. */
            String clientUuid
    ) {}

    /** Row shape for the production list. */
    public record ProductionSummary(
            Long id,
            String projectName,
            String shaftName,
            LocalDate productionDate,
            String shift,
            String periodType,
            BigDecimal quantity,
            String unitCode,
            BigDecimal targetQuantity,
            BigDecimal varianceQuantity,
            String status
    ) {}

    /** Full record for the production detail page. */
    public record ProductionDetail(
            Long id,
            Long projectId,
            String projectName,
            Long miningOperationId,
            Long shaftId,
            String shaftName,
            LocalDate productionDate,
            String shift,
            String periodType,
            BigDecimal oreTonnes,
            BigDecimal grade,
            BigDecimal recoveryPercent,
            BigDecimal goldRecovered,
            BigDecimal quantity,
            String unitCode,
            BigDecimal processingOutput,
            BigDecimal targetQuantity,
            BigDecimal varianceQuantity,
            String status,
            String recordedByName,
            String verifiedByName,
            LocalDateTime verifiedAt,
            String approvedByName,
            LocalDateTime approvedAt,
            Long correctsRecordId,
            String correctionReason,
            String source,
            String clientUuid,
            String notes,
            LocalDateTime createdAt,
            String createdBy
    ) {}

    /**
     * Creates a correction, never edits the original (SRS §14). {@code reason}
     * is mandatory — a correction with no stated reason is exactly the kind of
     * silent change the SRS forbids.
     */
    public record CorrectionRequest(
            @NotNull @PositiveOrZero BigDecimal quantity,
            BigDecimal oreTonnes,
            BigDecimal grade,
            @NotBlank String reason
    ) {}

    public record VerifyRequest(String comments) {}
}
