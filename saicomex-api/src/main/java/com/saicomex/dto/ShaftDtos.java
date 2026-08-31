package com.saicomex.dto;

import com.saicomex.entity.Shaft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §8. Request and response shapes for shafts — the primary operational
 * entity, each carrying its own financial and operational account.
 */
public final class ShaftDtos {

    private ShaftDtos() {}

    /** Create / update payload. */
    public record ShaftRequest(
            @NotNull Long projectId,
            Long miningOperationId,
            @NotBlank @Size(max = 30)  String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 40) String shaftNumber,
            String description,
            Long locationId,
            BigDecimal latitude,
            BigDecimal longitude,
            Long ownerPartnerId,
            Long shaftManagerId,
            BigDecimal depthMetres,
            LocalDate commissionedDate,
            LocalDate startDate,
            LocalDate closureDate,
            String status,
            BigDecimal productionTarget,
            @Size(max = 20) String productionTargetUnit,
            @Size(max = 20) String productionTargetPeriod,
            String notes
    ) {}

    /** Payload for {@code PATCH /api/shafts/{id}/status}. */
    public record StatusUpdateRequest(
            @NotBlank String status,
            String reason
    ) {}

    /** Row shape for the shafts list. */
    public record ShaftSummary(
            Long id,
            String code,
            String name,
            String shaftNumber,
            Long projectId,
            String projectName,
            Long operationId,
            String operationName,
            Long ownerPartnerId,
            String ownerPartnerName,
            String status,
            String contractStatus,
            BigDecimal productionTarget,
            String productionTargetUnit,
            LocalDate startDate
    ) {}

    /** Full record for the shaft detail page. */
    public record ShaftDetail(
            Long id,
            Long projectId,
            String projectName,
            Long miningOperationId,
            String operationName,
            String code,
            String name,
            String shaftNumber,
            String description,
            Long locationId,
            BigDecimal latitude,
            BigDecimal longitude,
            Long ownerPartnerId,
            String ownerPartnerName,
            Long shaftManagerId,
            String shaftManagerName,
            BigDecimal depthMetres,
            LocalDate commissionedDate,
            LocalDate startDate,
            LocalDate closureDate,
            String status,
            BigDecimal productionTarget,
            String productionTargetUnit,
            String productionTargetPeriod,
            String notes,
            Long activeContractId,
            String activeContractNumber,
            int documentCount,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime updatedAt,
            String updatedBy
    ) {}

    public static ShaftDetail toDetail(Shaft s, String projectName, String operationName,
                                       String ownerPartnerName, String shaftManagerName,
                                       Long activeContractId, String activeContractNumber,
                                       int documentCount) {
        return new ShaftDetail(
                s.getId(), s.getProjectId(), projectName,
                s.getMiningOperationId(), operationName,
                s.getCode(), s.getName(), s.getShaftNumber(), s.getDescription(),
                s.getLocationId(), s.getLatitude(), s.getLongitude(),
                s.getOwnerPartnerId(), ownerPartnerName,
                s.getShaftManagerId(), shaftManagerName,
                s.getDepthMetres(), s.getCommissionedDate(), s.getStartDate(), s.getClosureDate(),
                s.getStatus(), s.getProductionTarget(), s.getProductionTargetUnit(), s.getProductionTargetPeriod(),
                s.getNotes(), activeContractId, activeContractNumber, documentCount,
                s.getCreatedAt(), s.getCreatedBy(), s.getUpdatedAt(), s.getUpdatedBy());
    }
}
