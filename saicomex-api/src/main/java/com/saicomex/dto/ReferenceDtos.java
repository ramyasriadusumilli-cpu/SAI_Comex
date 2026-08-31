package com.saicomex.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only lookup data consumed to warm the SPA's dropdowns. No aggregate
 * owns these shapes, so they live together rather than joining one of the
 * other Dtos files.
 */
public final class ReferenceDtos {

    private ReferenceDtos() {}

    public record CurrencyDto(String code, String name, String symbol, Short decimalPlaces) {}

    public record ProductionUnitDto(String code, String name, String unitClass,
                                     BigDecimal baseFactor, Short decimalPlaces) {}

    public record ExpenseCategoryDto(Long id, String code, String name, Long parentId, String expenseClass) {}

    public record ContractTypeDto(Long id, String code, String name) {}

    public record AgreementRuleTypeDto(String code, String name, String stage, Integer defaultSequence) {}

    /** A role option for a dropdown — the full {@code RoleSummary} carries more than this needs. */
    public record RoleOption(Long id, String code, String name) {}

    public record ReportDefinitionDto(Long id, String code, String name, String reportGroup, String description,
                                      Boolean supportsPdf, Boolean supportsExcel, Boolean supportsCsv,
                                      String defaultPeriod) {}

    /** One call that warms every dropdown the SPA needs at startup. */
    public record ReferenceData(
            List<CurrencyDto> currencies,
            List<ProductionUnitDto> productionUnits,
            List<ExpenseCategoryDto> expenseCategories,
            List<ContractTypeDto> contractTypes,
            List<AgreementRuleTypeDto> agreementRuleTypes,
            List<RoleOption> roles,
            List<String> projectStatuses,
            List<String> shaftStatuses,
            List<String> operationStatuses,
            List<String> contractStatuses,
            List<String> operationTypes,
            List<ReportDefinitionDto> reportDefinitions
    ) {}
}
