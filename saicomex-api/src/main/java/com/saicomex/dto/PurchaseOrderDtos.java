package com.saicomex.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response records for purchase orders and goods receipt (SRS §19). */
public final class PurchaseOrderDtos {

    private PurchaseOrderDtos() {
    }

    public record PoLineRequest(
            Long itemId,
            @NotBlank String description,
            @NotNull @Positive BigDecimal quantity,
            String unit,
            @NotNull BigDecimal unitCost
    ) {}

    public record PoRequest(
            @NotNull Long supplierId,
            Long projectId,
            Long shaftId,
            Long storeId,
            @NotNull LocalDate orderDate,
            LocalDate expectedDate,
            @NotBlank String currency,
            BigDecimal taxAmount,
            String notes,
            @NotEmpty @Valid List<PoLineRequest> lines
    ) {}

    /** One line to receive: the line id and the quantity now arriving. */
    public record ReceiveLine(
            @NotNull Long lineId,
            @NotNull @Positive BigDecimal quantity
    ) {}

    public record ReceiveRequest(
            @NotEmpty @Valid List<ReceiveLine> lines
    ) {}

    public record PoLineDetail(
            Long id,
            int lineNo,
            Long itemId,
            String description,
            BigDecimal quantity,
            BigDecimal receivedQuantity,
            String unit,
            BigDecimal unitCost,
            BigDecimal lineTotal
    ) {}

    public record PoSummary(
            Long id,
            String poNumber,
            Long supplierId,
            LocalDate orderDate,
            LocalDate expectedDate,
            String currency,
            BigDecimal totalAmount,
            String status
    ) {}

    public record PoDetail(
            Long id,
            String poNumber,
            Long supplierId,
            Long projectId,
            Long shaftId,
            Long storeId,
            LocalDate orderDate,
            LocalDate expectedDate,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String status,
            String approvedBy,
            LocalDateTime approvedAt,
            String notes,
            List<PoLineDetail> lines
    ) {}
}
