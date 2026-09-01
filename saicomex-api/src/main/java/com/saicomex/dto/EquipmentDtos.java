package com.saicomex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request/response records for the equipment register and allocations (SRS §20-21). */
public final class EquipmentDtos {

    private EquipmentDtos() {
    }

    public record EquipmentRequest(
            @NotBlank String assetNumber,
            @NotBlank String name,
            @NotBlank String equipmentType,
            String description,
            String manufacturer,
            String model,
            String serialNumber,
            String registrationNumber,
            Integer yearOfManufacture,
            LocalDate purchaseDate,
            BigDecimal purchaseCost,
            String purchaseCurrency,
            BigDecimal currentValue,
            String ownership,
            Long ownerPartnerId,
            Long supplierId,
            Long projectId,
            Long miningOperationId,
            Long shaftId,
            Long operatorEmployeeId,
            BigDecimal operatingHours,
            BigDecimal serviceIntervalHours,
            LocalDate nextServiceDate,
            LocalDate insuranceExpiry,
            LocalDate licenceExpiry,
            String status,
            String notes
    ) {}

    /** Move equipment to a new placement — writes a new allocation, closing the current one. */
    public record AllocationRequest(
            @NotNull Long projectId,
            Long miningOperationId,
            Long shaftId,
            @NotNull LocalDate fromDate,
            Long operatorEmployeeId,
            BigDecimal openingHours,
            BigDecimal hireRate,
            String hireRateUnit,
            String rateCurrency,
            String reason
    ) {}

    public record AllocationDetail(
            Long id,
            Long projectId,
            Long miningOperationId,
            Long shaftId,
            LocalDate fromDate,
            LocalDate toDate,
            Long operatorEmployeeId,
            BigDecimal openingHours,
            BigDecimal closingHours,
            BigDecimal hireRate,
            String hireRateUnit,
            String reason,
            String createdBy
    ) {}

    public record EquipmentSummary(
            Long id,
            String assetNumber,
            String name,
            String equipmentType,
            String status,
            Long shaftId,
            BigDecimal operatingHours
    ) {}

    public record EquipmentDetail(
            Long id,
            String assetNumber,
            String name,
            String equipmentType,
            String description,
            String manufacturer,
            String model,
            String serialNumber,
            String registrationNumber,
            Integer yearOfManufacture,
            LocalDate purchaseDate,
            BigDecimal purchaseCost,
            String purchaseCurrency,
            BigDecimal currentValue,
            String ownership,
            Long ownerPartnerId,
            Long supplierId,
            Long projectId,
            Long miningOperationId,
            Long shaftId,
            Long operatorEmployeeId,
            BigDecimal operatingHours,
            BigDecimal serviceIntervalHours,
            LocalDate nextServiceDate,
            LocalDate insuranceExpiry,
            LocalDate licenceExpiry,
            String status,
            String notes
    ) {}
}
