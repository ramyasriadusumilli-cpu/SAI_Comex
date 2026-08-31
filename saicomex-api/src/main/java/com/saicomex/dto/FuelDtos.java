package com.saicomex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request/response records for fuel movements (SRS §17). A purchase receives
 * fuel into a store; an issue dispenses it and produces the three linked rows
 * (stock movement + expense + fuel record) at once.
 */
public final class FuelDtos {

    private FuelDtos() {
    }

    /** Receive fuel into a store — a stock RECEIPT plus a fuel record. */
    public record FuelPurchaseRequest(
            @NotNull Long itemId,
            @NotNull Long storeId,
            @NotBlank String fuelType,
            @NotNull @Positive BigDecimal quantityLitres,
            @NotNull @PositiveOrZero BigDecimal unitCost,
            @NotBlank String currency,
            Long supplierId,
            String reference,
            String notes,
            LocalDateTime transactionDate,
            String clientUuid
    ) {}

    /**
     * Dispense fuel to equipment or a shaft. Produces a stock ISSUE (valued at
     * the running average), a DIRECT expense on the shaft for that value, and
     * this fuel record linking both. {@code exchangeRate} freezes the expense's
     * base amount; {@code expenseCategoryId} is the category the auto-expense
     * is booked under.
     */
    public record FuelIssueRequest(
            @NotNull Long itemId,
            @NotNull Long storeId,
            @NotBlank String fuelType,
            @NotNull @Positive BigDecimal quantityLitres,
            String currency,
            BigDecimal exchangeRate,
            @NotNull Long projectId,
            Long miningOperationId,
            @NotNull Long shaftId,
            Long equipmentId,
            Long recipientEmployeeId,
            String recipientName,
            BigDecimal odometerReading,
            BigDecimal hourMeterReading,
            @NotNull Long expenseCategoryId,
            String reference,
            String notes,
            LocalDateTime transactionDate,
            String clientUuid
    ) {}

    public record FuelDetail(
            Long id,
            String transactionType,
            LocalDateTime transactionDate,
            String fuelType,
            Long itemId,
            Long storeId,
            BigDecimal quantityLitres,
            BigDecimal unitCost,
            BigDecimal totalCost,
            String currency,
            Long projectId,
            Long shaftId,
            Long equipmentId,
            Long inventoryTransactionId,
            Long expenseId,
            BigDecimal odometerReading,
            BigDecimal hourMeterReading,
            BigDecimal openingStock,
            BigDecimal closingStock,
            String reference,
            String createdBy
    ) {}
}
