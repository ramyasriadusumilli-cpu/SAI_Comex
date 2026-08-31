package com.saicomex.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** SRS §25 — partner settlement payloads. */
public final class SettlementDtos {

    private SettlementDtos() {}

    public record SettlementRequest(
            @NotNull Long shaftId,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd,
            String notes
    ) {}

    /**
     * A dry run. Same engine, same inputs, nothing written — so an operator can
     * see what a period would settle at before committing to a statement.
     */
    public record PreviewResult(
            Long shaftId,
            String shaftName,
            String partnerName,
            String contractNumber,
            String agreementName,
            LocalDate periodStart,
            LocalDate periodEnd,
            String currency,
            BigDecimal grossRevenue,
            BigDecimal totalDeductions,
            BigDecimal netDistributable,
            BigDecimal saicomexShare,
            BigDecimal partnerShare,
            BigDecimal partnerAdjustments,
            BigDecimal partnerNetPayable,
            BigDecimal totalProduction,
            String productionUnit,
            List<CalculationStepDto> steps,
            List<String> warnings
    ) {}

    public record CalculationStepDto(
            int stepNo,
            String stage,
            Long ruleId,
            String ruleType,
            String ruleName,
            String expression,
            BigDecimal inputAmount,
            BigDecimal percentApplied,
            BigDecimal rateApplied,
            BigDecimal resultAmount,
            BigDecimal runningBalance,
            String beneficiary,
            String notes
    ) {}

    public record SettlementSummary(
            Long id,
            String settlementNumber,
            String projectName,
            String shaftName,
            String partnerName,
            LocalDate periodStart,
            LocalDate periodEnd,
            String currency,
            BigDecimal grossRevenue,
            BigDecimal netDistributable,
            BigDecimal saicomexShare,
            BigDecimal partnerNetPayable,
            BigDecimal amountPaid,
            BigDecimal amountOutstanding,
            String status
    ) {}

    public record SettlementDetail(
            Long id,
            String settlementNumber,
            Long projectId,
            String projectName,
            Long shaftId,
            String shaftName,
            Long partnerId,
            String partnerName,
            Long contractId,
            String contractNumber,
            Long agreementId,
            String agreementName,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate settlementDate,
            String currency,
            BigDecimal grossRevenue,
            BigDecimal totalDeductions,
            BigDecimal netDistributable,
            BigDecimal saicomexShare,
            BigDecimal partnerShare,
            BigDecimal partnerAdjustments,
            BigDecimal partnerNetPayable,
            BigDecimal amountPaid,
            BigDecimal amountRetained,
            BigDecimal amountOutstanding,
            BigDecimal totalProduction,
            String productionUnit,
            BigDecimal totalExpenses,
            String status,
            LocalDateTime calculatedAt,
            String calculatedBy,
            String approvedBy,
            LocalDateTime approvedAt,
            String notes,
            List<CalculationStepDto> steps,
            List<SettlementLineDto> lines
    ) {}

    /** One source record the settlement consumed — the drill-down target of SRS §57. */
    public record SettlementLineDto(
            Long id,
            String lineType,
            String sourceTable,
            Long sourceId,
            LocalDate lineDate,
            String description,
            String categoryCode,
            BigDecimal quantity,
            String unitCode,
            BigDecimal amount,
            String currency,
            BigDecimal baseAmount,
            boolean included,
            String exclusionReason
    ) {}

    /** SRS §25 — the partner's position across every shaft they participate in. */
    public record PartnerStatement(
            Long partnerId,
            String partnerName,
            String currency,
            BigDecimal totalEarned,
            BigDecimal totalPaid,
            BigDecimal totalRetained,
            BigDecimal totalOutstanding,
            List<SettlementSummary> settlements
    ) {}

    public record ApprovalRequest(String comments) {}
}
