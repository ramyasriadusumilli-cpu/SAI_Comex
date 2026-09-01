package com.saicomex.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request/response records for maintenance jobs and parts (SRS §22). */
public final class MaintenanceDtos {

    private MaintenanceDtos() {
    }

    public record PartRequest(
            Long itemId,
            @NotBlank String description,
            @NotNull @Positive BigDecimal quantity,
            BigDecimal unitCost
    ) {}

    public record MaintenanceRequest(
            @NotNull Long equipmentId,
            @NotBlank String maintenanceType,
            String priority,
            LocalDate reportedDate,
            LocalDate serviceDate,
            BigDecimal hourMeterReading,
            @NotBlank String description,
            String workPerformed,
            String technicianName,
            Long technicianEmployeeId,
            Long supplierId,
            BigDecimal labourCost,
            BigDecimal otherCost,
            String currency,
            BigDecimal downtimeHours,
            Long projectId,
            Long shaftId,
            LocalDate nextServiceDate,
            String notes,
            @Valid List<PartRequest> parts
    ) {}

    public record PartDetail(
            Long id,
            Long itemId,
            String description,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalCost
    ) {}

    public record MaintenanceSummary(
            Long id,
            String jobNumber,
            Long equipmentId,
            String maintenanceType,
            String priority,
            LocalDate serviceDate,
            BigDecimal totalCost,
            String status
    ) {}

    public record MaintenanceDetail(
            Long id,
            String jobNumber,
            Long equipmentId,
            String maintenanceType,
            String priority,
            LocalDate reportedDate,
            LocalDate serviceDate,
            LocalDate completedDate,
            LocalDate nextServiceDate,
            BigDecimal hourMeterReading,
            String description,
            String workPerformed,
            String technicianName,
            Long supplierId,
            BigDecimal partsCost,
            BigDecimal labourCost,
            BigDecimal otherCost,
            BigDecimal totalCost,
            String currency,
            Long expenseId,
            BigDecimal downtimeHours,
            Long projectId,
            Long shaftId,
            String status,
            String notes,
            List<PartDetail> parts
    ) {}
}
