package com.saicomex.dto;

import com.saicomex.entity.CommercialAgreement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SRS §11. Request and response shapes for commercial agreements and their
 * rules — the configurable parameters the calculation engine (SRS §12, §60)
 * runs against. No percentage lives in Java; it all comes from these rows.
 */
public final class AgreementDtos {

    private AgreementDtos() {}

    /** One tier of a TIERED rule, e.g. "first 500g at 70/30, above that 60/40". */
    public record AgreementRuleTierRequest(
            @NotNull Integer tierNo,
            @NotNull BigDecimal fromValue,
            BigDecimal toValue,
            BigDecimal saicomexPercent,
            BigDecimal partnerPercent,
            BigDecimal fixedAmount,
            BigDecimal rateAmount
    ) {}

    /** One configurable rule within an agreement. */
    public record AgreementRuleRequest(
            @NotBlank String ruleType,
            @NotBlank @Size(max = 200) String name,
            String description,
            Integer sequenceNo,
            String scope,
            Long expenseCategoryId,
            @Size(max = 80) String scopeValue,
            @NotBlank String calculationMethod,
            BigDecimal saicomexPercent,
            BigDecimal partnerPercent,
            BigDecimal fixedAmount,
            BigDecimal rateAmount,
            @Size(max = 20) String rateUnit,
            @Size(max = 3) String currency,
            String borneBy,
            Boolean deductBeforeSplit,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal capPercent,
            BigDecimal recoverableTotal,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean isActive,
            String notes,
            @Valid List<AgreementRuleTierRequest> tiers
    ) {}

    /** Create / update payload — the header plus its whole rule set. */
    public record AgreementRequest(
            @NotNull Long contractId,
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String settlementBasis,
            BigDecimal defaultSaicomexPercent,
            BigDecimal defaultPartnerPercent,
            @Size(max = 3) String currency,
            Short roundingScale,
            String roundingMode,
            String notes,
            @Valid List<AgreementRuleRequest> rules
    ) {}

    /** Row shape for the agreements-by-contract list. */
    public record AgreementSummary(
            Long id,
            Long contractId,
            String name,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String settlementBasis,
            String currency,
            int ruleCount
    ) {}

    public record AgreementRuleTierDto(
            Long id,
            Integer tierNo,
            BigDecimal fromValue,
            BigDecimal toValue,
            BigDecimal saicomexPercent,
            BigDecimal partnerPercent,
            BigDecimal fixedAmount,
            BigDecimal rateAmount
    ) {}

    public record AgreementRuleDetail(
            Long id,
            String ruleType,
            String name,
            String description,
            Integer sequenceNo,
            String scope,
            Long expenseCategoryId,
            String scopeValue,
            String calculationMethod,
            BigDecimal saicomexPercent,
            BigDecimal partnerPercent,
            BigDecimal fixedAmount,
            BigDecimal rateAmount,
            String rateUnit,
            String currency,
            String borneBy,
            Boolean deductBeforeSplit,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal capPercent,
            BigDecimal recoverableTotal,
            BigDecimal recoveredToDate,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean isActive,
            String notes,
            List<AgreementRuleTierDto> tiers
    ) {}

    /** The {@link com.saicomex.entity.AgreementRuleType} catalogue, for the rule builder UI. */
    public record RuleTypeOption(
            String code,
            String name,
            String description,
            String stage,
            Integer defaultSequence
    ) {}

    /** Full record for the agreement detail page. */
    public record AgreementDetail(
            Long id,
            Long contractId,
            String contractNumber,
            String shaftName,
            String partnerName,
            Long contractVersionId,
            String name,
            String description,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String status,
            String settlementBasis,
            BigDecimal defaultSaicomexPercent,
            BigDecimal defaultPartnerPercent,
            String currency,
            Short roundingScale,
            String roundingMode,
            String notes,
            String approvedBy,
            LocalDateTime approvedAt,
            List<AgreementRuleDetail> rules,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime updatedAt,
            String updatedBy
    ) {}

    public static AgreementDetail toDetail(CommercialAgreement a, String contractNumber, String shaftName,
                                           String partnerName, List<AgreementRuleDetail> rules) {
        return new AgreementDetail(
                a.getId(), a.getContractId(), contractNumber, shaftName, partnerName, a.getContractVersionId(),
                a.getName(), a.getDescription(), a.getEffectiveFrom(), a.getEffectiveTo(), a.getStatus(),
                a.getSettlementBasis(), a.getDefaultSaicomexPercent(), a.getDefaultPartnerPercent(),
                a.getCurrency(), a.getRoundingScale(), a.getRoundingMode(), a.getNotes(),
                a.getApprovedBy(), a.getApprovedAt(), rules,
                a.getCreatedAt(), a.getCreatedBy(), a.getUpdatedAt(), a.getUpdatedBy());
    }
}
