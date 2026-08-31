package com.saicomex.engine;

import java.math.BigDecimal;

/**
 * One line of the SRS §12 audit trail: what happened, to what, why, and what
 * came out. Persisted verbatim into {@code settlement_calculations}.
 *
 * @param stepNo         execution order — reading these in order is the explanation
 * @param stage          DEDUCTION | ALLOCATION | ADJUSTMENT | TOTAL
 * @param ruleId         the agreement rule that produced this line, if any
 * @param ruleType       its rule type code
 * @param ruleName       its human name, copied so the statement survives a rule rename
 * @param expression     the arithmetic in words, e.g. "75,000.00 × 70.000000% = 52,500.00"
 * @param inputAmount    what the rule was applied to
 * @param percentApplied the percentage used, if the method was PERCENTAGE
 * @param rateApplied    the rate used, if the method was RATE_PER_UNIT
 * @param resultAmount   the amount this step produced
 * @param runningBalance the distributable pool after this step
 * @param beneficiary    SAICOMEX | PARTNER | NONE — who the amount accrues to
 * @param notes          anything an operator would otherwise have to ask about
 */
public record CalculationStep(
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
) {
    public static final String STAGE_DEDUCTION  = "DEDUCTION";
    public static final String STAGE_ALLOCATION = "ALLOCATION";
    public static final String STAGE_ADJUSTMENT = "ADJUSTMENT";
    public static final String STAGE_TOTAL      = "TOTAL";

    public static final String SAICOMEX = "SAICOMEX";
    public static final String PARTNER  = "PARTNER";
    public static final String NONE     = "NONE";
}
