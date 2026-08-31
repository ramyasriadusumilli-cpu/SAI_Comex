package com.saicomex.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * The output of a settlement calculation: the headline figures plus the full
 * step-by-step derivation that produced them.
 *
 * @param grossRevenue       revenue in, before anything is taken off
 * @param totalDeductions    everything removed before the contractual split
 * @param netDistributable   grossRevenue − totalDeductions
 * @param saicomexShare      SAIComex's allocation, before adjustments
 * @param partnerShare       the partner's allocation, before adjustments
 * @param saicomexAdjustments net post-allocation movement on SAIComex's side
 * @param partnerAdjustments  net post-allocation movement on the partner's side
 * @param partnerNetPayable  partnerShare + partnerAdjustments — what is owed
 * @param steps              the SRS §12 audit trail, in execution order
 * @param warnings           conditions an operator should see before approving
 */
public record CalculationResult(
        BigDecimal grossRevenue,
        BigDecimal totalDeductions,
        BigDecimal netDistributable,
        BigDecimal saicomexShare,
        BigDecimal partnerShare,
        BigDecimal saicomexAdjustments,
        BigDecimal partnerAdjustments,
        BigDecimal partnerNetPayable,
        List<CalculationStep> steps,
        List<String> warnings
) {}
