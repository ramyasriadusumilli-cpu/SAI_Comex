package com.saicomex.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** SRS §15, §16 — expense capture, allocation and approval routing. */
public final class ExpenseDtos {

    private ExpenseDtos() {}

    /** Create / update payload. {@code allocations} is read for MANUAL/PERCENTAGE methods only. */
    public record ExpenseRequest(
            @NotNull Long projectId,
            Long miningOperationId,
            Long shaftId,
            @NotNull Long categoryId,
            Long supplierId,
            @NotNull LocalDate expenseDate,
            @NotBlank String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitCost,
            @NotNull @PositiveOrZero BigDecimal amount,
            @NotBlank String currency,
            @NotNull BigDecimal exchangeRate,
            BigDecimal taxAmount,
            String allocationMethod,
            Boolean isShared,
            String reference,
            String invoiceNumber,
            String paymentMethod,
            String notes,
            String clientUuid,
            @Valid List<AllocationRequest> allocations
    ) {}

    /** One shaft's share. Callers may supply either {@code allocationPercent} or a raw {@code amount}. */
    public record AllocationRequest(
            @NotNull Long shaftId,
            BigDecimal allocationPercent,
            BigDecimal allocationQuantity,
            BigDecimal amount,
            String basisNote
    ) {}

    public record ExpenseSummary(
            Long id,
            String expenseNumber,
            String projectName,
            String shaftName,
            String categoryName,
            LocalDate expenseDate,
            String description,
            BigDecimal amount,
            String currency,
            BigDecimal baseAmount,
            String status,
            String approvalStage
    ) {}

    public record ExpenseDetail(
            Long id,
            String expenseNumber,
            Long projectId,
            String projectName,
            Long miningOperationId,
            Long shaftId,
            String shaftName,
            Long categoryId,
            String categoryName,
            Long supplierId,
            LocalDate expenseDate,
            String description,
            BigDecimal quantity,
            String unit,
            BigDecimal unitCost,
            BigDecimal amount,
            String currency,
            BigDecimal exchangeRate,
            BigDecimal baseAmount,
            BigDecimal taxAmount,
            String allocationMethod,
            boolean isShared,
            String reference,
            String invoiceNumber,
            String paymentMethod,
            String status,
            String approvalStage,
            String submittedByName,
            String approvedByName,
            LocalDateTime approvedAt,
            String rejectionReason,
            LocalDateTime paidAt,
            String notes,
            LocalDateTime createdAt,
            String createdBy,
            List<AllocationDto> allocations
    ) {}

    public record AllocationDto(
            Long id,
            Long shaftId,
            String shaftName,
            BigDecimal allocationPercent,
            BigDecimal allocationQuantity,
            BigDecimal amount,
            BigDecimal baseAmount,
            String basisNote
    ) {}

    public record ApprovalDecision(String comments) {}

    public record RejectionRequest(@NotBlank String reason) {}
}
