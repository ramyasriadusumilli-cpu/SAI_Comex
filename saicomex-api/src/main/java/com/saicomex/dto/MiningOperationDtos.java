package com.saicomex.dto;

import com.saicomex.entity.MiningOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §7. Request and response shapes for mining operations — the level
 * between a project and its shafts.
 */
public final class MiningOperationDtos {

    private MiningOperationDtos() {}

    /** Create / update payload. */
    public record OperationRequest(
            @NotNull Long projectId,
            @NotBlank @Size(max = 30)  String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 60)  String operationType,
            String description,
            Long locationId,
            BigDecimal latitude,
            BigDecimal longitude,
            Long managerId,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            BigDecimal budgetAmount,
            @Size(max = 3) String budgetCurrency,
            String notes
    ) {}

    /** Row shape for the operations list. */
    public record OperationSummary(
            Long id,
            String code,
            String name,
            String operationType,
            String status,
            Long projectId,
            String projectName,
            String managerName,
            LocalDate startDate,
            int shaftCount,
            int activeShaftCount,
            BigDecimal budgetAmount,
            String budgetCurrency
    ) {}

    /** Full record for the operation detail page. */
    public record OperationDetail(
            Long id,
            Long projectId,
            String projectName,
            String code,
            String name,
            String operationType,
            String description,
            Long locationId,
            BigDecimal latitude,
            BigDecimal longitude,
            Long managerId,
            String managerName,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            BigDecimal budgetAmount,
            String budgetCurrency,
            String notes,
            int shaftCount,
            int documentCount,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime updatedAt,
            String updatedBy
    ) {}

    public static OperationDetail toDetail(MiningOperation o, String projectName, String managerName,
                                           int shaftCount, int documentCount) {
        return new OperationDetail(
                o.getId(), o.getProjectId(), projectName,
                o.getCode(), o.getName(), o.getOperationType(), o.getDescription(),
                o.getLocationId(), o.getLatitude(), o.getLongitude(),
                o.getManagerId(), managerName,
                o.getStartDate(), o.getEndDate(), o.getStatus(),
                o.getBudgetAmount(), o.getBudgetCurrency(), o.getNotes(),
                shaftCount, documentCount,
                o.getCreatedAt(), o.getCreatedBy(), o.getUpdatedAt(), o.getUpdatedBy());
    }
}
