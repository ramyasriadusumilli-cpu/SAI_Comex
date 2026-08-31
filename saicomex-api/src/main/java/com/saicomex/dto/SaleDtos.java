package com.saicomex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** SRS §23 — sale / revenue capture. */
public final class SaleDtos {

    private SaleDtos() {}

    public record SaleRequest(
            @NotNull Long projectId,
            Long miningOperationId,
            Long shaftId,
            Long contractId,
            Long batchId,
            Long buyerId,
            @NotNull LocalDate saleDate,
            String product,
            @NotNull @Positive BigDecimal quantity,
            @NotBlank String unitCode,
            BigDecimal grade,
            String assayReference,
            BigDecimal assayPercent,
            @NotNull @Positive BigDecimal unitPrice,
            @NotBlank String currency,
            @NotNull BigDecimal exchangeRate,
            BigDecimal deductionsAmount,
            BigDecimal taxAmount,
            BigDecimal royaltyAmount,
            String invoiceNumber,
            String reference,
            String notes
    ) {}

    public record SaleSummary(
            Long id,
            String saleNumber,
            String projectName,
            String shaftName,
            String buyerName,
            LocalDate saleDate,
            String product,
            BigDecimal quantity,
            String unitCode,
            BigDecimal netAmount,
            String currency,
            String status,
            String settlementStatus
    ) {}

    public record SaleDetail(
            Long id,
            String saleNumber,
            Long projectId,
            String projectName,
            Long miningOperationId,
            Long shaftId,
            String shaftName,
            Long contractId,
            Long batchId,
            Long buyerId,
            String buyerName,
            LocalDate saleDate,
            String product,
            BigDecimal quantity,
            String unitCode,
            BigDecimal grade,
            String assayReference,
            BigDecimal assayPercent,
            BigDecimal unitPrice,
            String currency,
            BigDecimal exchangeRate,
            BigDecimal grossAmount,
            BigDecimal deductionsAmount,
            BigDecimal taxAmount,
            BigDecimal royaltyAmount,
            BigDecimal netAmount,
            BigDecimal grossBaseAmount,
            BigDecimal netBaseAmount,
            String paymentStatus,
            BigDecimal amountReceived,
            String settlementStatus,
            Long settlementId,
            String invoiceNumber,
            String reference,
            String status,
            String notes,
            LocalDateTime createdAt,
            String createdBy
    ) {}
}
