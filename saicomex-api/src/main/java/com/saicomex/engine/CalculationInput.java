package com.saicomex.engine;

import com.saicomex.entity.AgreementRule;
import com.saicomex.entity.AgreementRuleTier;
import com.saicomex.entity.CommercialAgreement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the engine is allowed to see.
 *
 * <p>The engine takes no repositories. It is a pure function of this input,
 * which is what makes a settlement reproducible: feed the same input a year
 * later and the same statement comes out, regardless of what has changed in
 * the database since.
 *
 * @param agreement       the agreement version that governs this period
 * @param rules           its rules, already filtered to those effective in the period, in sequence order
 * @param tiersByRuleId   tier rows for any TIERED rule
 * @param periodStart     inclusive
 * @param periodEnd       inclusive
 * @param currency        settlement currency
 * @param grossRevenue    total confirmed revenue for the shaft in the period
 * @param costsByCategoryCode approved expense totals for the shaft in the period, keyed by
 *                        expense category code (DIESEL, LABOUR, …)
 * @param capexCategoryCodes  which of those codes are CAPEX rather than OPEX
 * @param totalProduction production quantity in the period, in {@code productionUnit}
 * @param productionUnit  unit code the production figure is expressed in
 */
public record CalculationInput(
        CommercialAgreement agreement,
        List<AgreementRule> rules,
        Map<Long, List<AgreementRuleTier>> tiersByRuleId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currency,
        BigDecimal grossRevenue,
        Map<String, BigDecimal> costsByCategoryCode,
        java.util.Set<String> capexCategoryCodes,
        BigDecimal totalProduction,
        String productionUnit
) {}
