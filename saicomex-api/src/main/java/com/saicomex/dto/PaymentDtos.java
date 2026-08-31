package com.saicomex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** SRS §27 — payments to partners, suppliers, employees and contractors. */
public final class PaymentDtos {

    private PaymentDtos() {}

    public record PaymentRequest(
            @NotBlank String paymentType,
            @NotNull LocalDate paymentDate,
            Long partnerId,
            Long supplierId,
            Long employeeId,
            @NotBlank String recipientName,
            Long projectId,
            Long miningOperationId,
            Long shaftId,
            Long settlementId,
            Long expenseId,
            Long categoryId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @NotNull BigDecimal exchangeRate,
            @NotBlank String paymentMethod,
            String bankReference,
            String reference,
            String notes
    ) {}

    public record PaymentSummary(
            Long id,
            String paymentNumber,
            String paymentType,
            LocalDate paymentDate,
            String recipientName,
            BigDecimal amount,
            String currency,
            BigDecimal baseAmount,
            String status
    ) {}

    public record PaymentDetail(
            Long id,
            String paymentNumber,
            String paymentType,
            LocalDate paymentDate,
            Long partnerId,
            String partnerName,
            Long supplierId,
            Long employeeId,
            String recipientName,
            Long projectId,
            String projectName,
            Long miningOperationId,
            Long shaftId,
            String shaftName,
            Long settlementId,
            String settlementNumber,
            Long expenseId,
            Long categoryId,
            BigDecimal amount,
            String currency,
            BigDecimal exchangeRate,
            BigDecimal baseAmount,
            String paymentMethod,
            String bankReference,
            String reference,
            String status,
            String approvedBy,
            LocalDateTime approvedAt,
            String notes,
            LocalDateTime createdAt,
            String createdBy
    ) {}
}
