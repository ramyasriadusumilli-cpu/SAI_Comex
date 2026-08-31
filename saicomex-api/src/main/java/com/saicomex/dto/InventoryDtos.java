package com.saicomex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request/response records for inventory items, stores, stock movements and
 * balances (SRS §18, §19).
 */
public final class InventoryDtos {

    private InventoryDtos() {
    }

    // ------------------------------------------------------------------ items

    public record ItemRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String itemType,
            Long categoryId,
            @NotBlank String unit,
            Boolean isControlled,
            Boolean requiresPermit,
            BigDecimal minimumStock,
            BigDecimal maximumStock,
            BigDecimal reorderLevel,
            BigDecimal standardCost,
            String costCurrency,
            String valuationMethod,
            Boolean isActive,
            String notes
    ) {}

    public record ItemDetail(
            Long id,
            String code,
            String name,
            String itemType,
            Long categoryId,
            String unit,
            boolean isControlled,
            boolean requiresPermit,
            BigDecimal minimumStock,
            BigDecimal maximumStock,
            BigDecimal reorderLevel,
            BigDecimal standardCost,
            String costCurrency,
            String valuationMethod,
            boolean isActive,
            String notes
    ) {}

    // ----------------------------------------------------------------- stores

    public record StoreRequest(
            @NotBlank String code,
            @NotBlank String name,
            Long projectId,
            Long shaftId,
            Long locationId,
            @NotBlank String storeType,
            Long keeperUserId,
            Boolean isActive
    ) {}

    public record StoreDetail(
            Long id,
            String code,
            String name,
            Long projectId,
            Long shaftId,
            Long locationId,
            String storeType,
            Long keeperUserId,
            boolean isActive
    ) {}

    // ----------------------------------------------------------- transactions

    /**
     * A stock movement. {@code quantity} is always the positive magnitude — the
     * service derives the sign from {@code transactionType}. For a controlled
     * item ISSUE, {@code permitReference} and a recipient are required.
     */
    public record TransactionRequest(
            @NotNull Long itemId,
            @NotNull Long storeId,
            @NotBlank String transactionType,
            @NotNull @Positive BigDecimal quantity,
            BigDecimal unitCost,
            String currency,
            Long projectId,
            Long miningOperationId,
            Long shaftId,
            Long equipmentId,
            Long recipientEmployeeId,
            String recipientName,
            Long supplierId,
            Long transferStoreId,
            String permitReference,
            @NotBlank String reason,
            String reference,
            LocalDateTime transactionDate,
            String clientUuid
    ) {}

    public record TransactionDetail(
            Long id,
            String transactionNumber,
            Long itemId,
            String itemCode,
            String itemName,
            Long storeId,
            String storeName,
            String transactionType,
            LocalDateTime transactionDate,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalCost,
            String currency,
            BigDecimal balanceAfter,
            Long shaftId,
            Long expenseId,
            String permitReference,
            String recipientName,
            String reason,
            String reference,
            String createdBy
    ) {}

    // --------------------------------------------------------------- balances

    public record BalanceDetail(
            Long itemId,
            String itemCode,
            String itemName,
            String unit,
            Long storeId,
            String storeName,
            BigDecimal quantity,
            BigDecimal averageCost,
            String costCurrency,
            LocalDateTime lastMovementAt
    ) {}
}
