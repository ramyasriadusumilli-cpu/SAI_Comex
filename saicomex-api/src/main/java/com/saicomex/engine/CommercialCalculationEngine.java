package com.saicomex.engine;

import com.saicomex.entity.AgreementRule;
import com.saicomex.entity.AgreementRuleTier;
import com.saicomex.entity.CommercialAgreement;
import com.saicomex.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SRS §11 and §12 — the commercial agreement engine and the calculation
 * waterfall that runs on top of it.
 *
 * <h2>The rule this class exists to honour</h2>
 * SRS §60: "No hard-coded business rule should be introduced where the
 * business requirement may vary by project, shaft or contract." There is
 * therefore no percentage, no cost category and no ordering decision written
 * into this file. Everything comes from {@code agreement_rules} rows. The
 * only thing hard-coded here is the <em>shape</em> of the waterfall, which
 * SRS §12 fixes:
 *
 * <pre>
 *   Gross revenue
 *     − deductions (rules with stage DEDUCTION, in sequence order)
 *   = net distributable
 *     × contractual allocation (stage ALLOCATION)
 *   = SAIComex share + partner share
 *     ± adjustments (stage ADJUSTMENT)
 *   = partner net payable
 * </pre>
 *
 * <h2>Two ways a cost can be shared, and why both are needed</h2>
 * A cost-share rule can act in either of two places, chosen by its
 * {@code deductBeforeSplit} flag:
 * <ul>
 *   <li><b>Before the split</b> — the cost comes off gross revenue, so both
 *       parties absorb it in whatever ratio the allocation rule sets. This is
 *       the SRS §25 worked example: 110,000 gross − 35,000 costs = 75,000
 *       distributable, split 70/30.</li>
 *   <li><b>After the split</b> — the cost is charged to one side (or split by
 *       the rule's <em>own</em> percentages, which need not match the revenue
 *       split) and applied as an adjustment. This is what expresses "revenue
 *       is 70/30 but diesel is 50/50", which a single global percentage
 *       cannot.</li>
 * </ul>
 *
 * <h2>Double-counting</h2>
 * Cost rules run in sequence order and each one <em>consumes</em> the expense
 * categories it matched. A specific rule (FUEL_COST_SHARE) placed before a
 * general one (OPEX_SHARE) therefore takes fuel out of the pool, and the
 * general rule sees only what is left. Ordering is the operator's lever, and
 * it is why {@code sequence_no} is part of the contract rather than an
 * implementation detail. Any category left unconsumed at the end of the
 * deduction stage is reported as a warning rather than silently dropped or
 * silently deducted — the two ways a settlement quietly goes wrong.
 */
@Component
public class CommercialCalculationEngine {

    /**
     * Which expense category codes each cost-share rule type covers by default.
     *
     * <p>This is a convenience mapping, not a business rule: a rule may set
     * {@code scope = EXPENSE_CATEGORY} and name exactly one category, which
     * overrides the mapping entirely. The defaults exist so that the common
     * case — "fuel is shared 50/50" — does not require the operator to
     * enumerate DIESEL, PETROL and OIL by hand.
     */
    private static final Map<String, Set<String>> DEFAULT_CATEGORIES = Map.of(
            "FUEL_COST_SHARE",       Set.of("DIESEL", "PETROL", "OIL"),
            "EXPLOSIVE_COST_SHARE",  Set.of("EXPLOSIVES"),
            "LABOUR_COST_SHARE",     Set.of("LABOUR", "CONTRACTOR"),
            "EQUIPMENT_COST_SHARE",  Set.of("EQUIPMENT", "REPAIRS", "MAINTENANCE", "SPARES"),
            "PROCESSING_COST_SHARE", Set.of("PROCESSING", "LABORATORY", "CHEMICALS"),
            "TRANSPORT_COST_SHARE",  Set.of("TRANSPORT")
    );

    /** Rule types that take an amount off the pool and credit it to SAIComex. */
    private static final Set<String> SAICOMEX_RETENTIONS =
            Set.of("MANAGEMENT_FEE", "CAPITAL_RECOVERY");

    private static final DecimalFormat MONEY   = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PERCENT = new DecimalFormat("0.######");

    public CalculationResult calculate(CalculationInput in) {
        CommercialAgreement agreement = in.agreement();
        int scale = agreement.getRoundingScale() == null ? 2 : agreement.getRoundingScale();
        RoundingMode mode = roundingMode(agreement.getRoundingMode());

        List<CalculationStep> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int step = 0;

        BigDecimal gross = nz(in.grossRevenue()).setScale(scale, mode);
        BigDecimal pool = gross;

        steps.add(new CalculationStep(++step, CalculationStep.STAGE_TOTAL, null, null, "Gross revenue",
                "Confirmed sales for the period = " + MONEY.format(gross),
                null, null, null, gross, gross, CalculationStep.NONE,
                "Period " + in.periodStart() + " to " + in.periodEnd()));

        // ------------------------------------------------------------------
        // Stage 1 — deductions
        // ------------------------------------------------------------------
        Map<String, BigDecimal> costs = in.costsByCategoryCode();
        Set<String> consumed = new LinkedHashSet<>();
        BigDecimal totalDeductions = BigDecimal.ZERO.setScale(scale, mode);
        BigDecimal saicomexAdjustments = BigDecimal.ZERO.setScale(scale, mode);
        BigDecimal partnerAdjustments = BigDecimal.ZERO.setScale(scale, mode);

        List<AgreementRule> deductionRules = in.rules().stream()
                .filter(r -> isCostShare(r.getRuleType()) || isRetention(r.getRuleType())
                          || "SPECIAL_DEDUCTION".equals(r.getRuleType()))
                .toList();

        for (AgreementRule rule : deductionRules) {
            if (isCostShare(rule.getRuleType())) {
                CostOutcome outcome = applyCostRule(rule, in, costs, consumed, scale, mode);
                if (outcome == null) continue;

                if (Boolean.TRUE.equals(rule.getDeductBeforeSplit())) {
                    pool = pool.subtract(outcome.amount());
                    totalDeductions = totalDeductions.add(outcome.amount());
                    steps.add(new CalculationStep(++step, CalculationStep.STAGE_DEDUCTION,
                            rule.getId(), rule.getRuleType(), rule.getName(),
                            MONEY.format(gross) + " pool less " + outcome.description()
                                    + " = " + MONEY.format(pool),
                            outcome.matchedTotal(), rule.getSaicomexPercent(), rule.getRateAmount(),
                            outcome.amount().negate(), pool, CalculationStep.NONE,
                            "Borne by both parties in the allocation ratio; categories: " + outcome.categories()));
                } else {
                    // Charged after allocation, so the pool is untouched and each
                    // side carries its portion as an adjustment.
                    if (outcome.saicomexPortion().signum() != 0) {
                        saicomexAdjustments = saicomexAdjustments.subtract(outcome.saicomexPortion());
                        steps.add(new CalculationStep(++step, CalculationStep.STAGE_ADJUSTMENT,
                                rule.getId(), rule.getRuleType(), rule.getName(),
                                "SAIComex bears " + MONEY.format(outcome.saicomexPortion())
                                        + " of " + outcome.description(),
                                outcome.matchedTotal(), rule.getSaicomexPercent(), null,
                                outcome.saicomexPortion().negate(), pool, CalculationStep.SAICOMEX,
                                "Categories: " + outcome.categories()));
                    }
                    if (outcome.partnerPortion().signum() != 0) {
                        partnerAdjustments = partnerAdjustments.subtract(outcome.partnerPortion());
                        steps.add(new CalculationStep(++step, CalculationStep.STAGE_ADJUSTMENT,
                                rule.getId(), rule.getRuleType(), rule.getName(),
                                "Partner bears " + MONEY.format(outcome.partnerPortion())
                                        + " of " + outcome.description(),
                                outcome.matchedTotal(), rule.getPartnerPercent(), null,
                                outcome.partnerPortion().negate(), pool, CalculationStep.PARTNER,
                                "Categories: " + outcome.categories()));
                    }
                }
            } else {
                // Management fee, capital recovery, special deduction: an amount
                // computed from the pool, production or a fixed figure.
                BigDecimal amount = computeAmount(rule, pool, in.totalProduction(), scale, mode);
                amount = applyBounds(rule, amount, pool, scale, mode);
                if (amount.signum() == 0) continue;

                pool = pool.subtract(amount);
                totalDeductions = totalDeductions.add(amount);
                // A management fee or capital recovery leaves the pool and lands
                // in SAIComex's pocket. A special deduction leaves the pool and
                // goes wherever the contract says — the engine does not assume
                // a beneficiary it was not told about.
                boolean credited = SAICOMEX_RETENTIONS.contains(rule.getRuleType());
                if (credited) saicomexAdjustments = saicomexAdjustments.add(amount);

                steps.add(new CalculationStep(++step, CalculationStep.STAGE_DEDUCTION,
                        rule.getId(), rule.getRuleType(), rule.getName(),
                        describeAmount(rule, amount, pool.add(amount)) + " → pool " + MONEY.format(pool),
                        pool.add(amount), rule.getSaicomexPercent(), rule.getRateAmount(),
                        amount.negate(), pool,
                        credited ? CalculationStep.SAICOMEX : CalculationStep.NONE,
                        credited ? "Deducted from the pool and retained by SAIComex" : null));
            }
        }

        // Categories nobody claimed. Neither deducting nor ignoring them
        // silently is acceptable, so say so.
        Set<String> unconsumed = new LinkedHashSet<>(costs.keySet());
        unconsumed.removeAll(consumed);
        unconsumed.removeIf(code -> nz(costs.get(code)).signum() == 0);
        if (!unconsumed.isEmpty()) {
            BigDecimal unclaimed = unconsumed.stream()
                    .map(c -> nz(costs.get(c)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(scale, mode);
            warnings.add("No agreement rule covers " + MONEY.format(unclaimed)
                    + " of expenditure in " + unconsumed
                    + ". It has NOT been deducted. Add a cost-share rule, or confirm SAIComex absorbs it outside this settlement.");
        }

        BigDecimal netDistributable = pool.setScale(scale, mode);
        steps.add(new CalculationStep(++step, CalculationStep.STAGE_TOTAL, null, null, "Net distributable",
                MONEY.format(gross) + " − " + MONEY.format(totalDeductions) + " = " + MONEY.format(netDistributable),
                gross, null, null, netDistributable, netDistributable, CalculationStep.NONE, null));

        // ------------------------------------------------------------------
        // Stage 2 — allocation
        // ------------------------------------------------------------------
        AgreementRule allocationRule = findAllocationRule(in);
        BigDecimal saicomexPercent;
        BigDecimal partnerPercent;
        Long allocationRuleId = null;
        String allocationRuleName = "Default agreement split";
        String allocationRuleType = null;

        if (allocationRule != null) {
            allocationRuleId = allocationRule.getId();
            allocationRuleName = allocationRule.getName();
            allocationRuleType = allocationRule.getRuleType();
            if ("TIERED".equals(allocationRule.getCalculationMethod())) {
                AgreementRuleTier tier = resolveTier(
                        in.tiersByRuleId().get(allocationRule.getId()),
                        basisValueForTier(in, netDistributable));
                if (tier == null) {
                    throw new BusinessRuleException(
                            "Rule '" + allocationRule.getName() + "' is tiered but no tier covers the period's value. "
                            + "Add an open-ended top tier to the agreement.");
                }
                saicomexPercent = nz(tier.getSaicomexPercent());
                partnerPercent = nz(tier.getPartnerPercent());
                allocationRuleName = allocationRule.getName() + " (tier " + tier.getTierNo() + ")";
            } else {
                saicomexPercent = nz(allocationRule.getSaicomexPercent());
                partnerPercent = nz(allocationRule.getPartnerPercent());
            }
        } else {
            saicomexPercent = nz(agreement.getDefaultSaicomexPercent());
            partnerPercent = nz(agreement.getDefaultPartnerPercent());
            if (saicomexPercent.signum() == 0 && partnerPercent.signum() == 0) {
                throw new BusinessRuleException(
                        "The commercial agreement has no allocation rule and no default split. "
                        + "Add a REVENUE_SHARE (or PRODUCTION_SHARE / PROFIT_SHARE) rule, or set the default percentages.");
            }
        }

        BigDecimal sum = saicomexPercent.add(partnerPercent);
        if (sum.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new BusinessRuleException(
                    "The allocation split is " + PERCENT.format(sum) + "%, not 100%. "
                    + "Correct the agreement before calculating a settlement.");
        }

        BigDecimal saicomexShare = percentOf(netDistributable, saicomexPercent, scale, mode);
        // The partner takes the remainder rather than its own rounded
        // percentage, so the two shares always reconstitute the pool exactly.
        // Rounding each side independently loses or invents a cent, and a cent
        // that nobody owns is a reconciliation query every single month.
        BigDecimal partnerShare = netDistributable.subtract(saicomexShare).setScale(scale, mode);

        steps.add(new CalculationStep(++step, CalculationStep.STAGE_ALLOCATION,
                allocationRuleId, allocationRuleType, allocationRuleName,
                MONEY.format(netDistributable) + " × " + PERCENT.format(saicomexPercent) + "% = " + MONEY.format(saicomexShare),
                netDistributable, saicomexPercent, null, saicomexShare, netDistributable,
                CalculationStep.SAICOMEX, null));

        steps.add(new CalculationStep(++step, CalculationStep.STAGE_ALLOCATION,
                allocationRuleId, allocationRuleType, allocationRuleName,
                MONEY.format(netDistributable) + " − " + MONEY.format(saicomexShare) + " = " + MONEY.format(partnerShare)
                        + " (" + PERCENT.format(partnerPercent) + "%)",
                netDistributable, partnerPercent, null, partnerShare, netDistributable,
                CalculationStep.PARTNER, "Taken as the remainder so the two shares total the pool exactly"));

        // ------------------------------------------------------------------
        // Stage 3 — post-allocation adjustments
        // ------------------------------------------------------------------
        for (AgreementRule rule : in.rules()) {
            String type = rule.getRuleType();
            if ("ADVANCE_RECOVERY".equals(type) || "PENALTY".equals(type)) {
                BigDecimal base = partnerShare.add(partnerAdjustments);
                BigDecimal amount = applyBounds(rule,
                        computeAmount(rule, base, in.totalProduction(), scale, mode), base, scale, mode);
                if (amount.signum() == 0) continue;
                partnerAdjustments = partnerAdjustments.subtract(amount);
                saicomexAdjustments = saicomexAdjustments.add(amount);
                steps.add(new CalculationStep(++step, CalculationStep.STAGE_ADJUSTMENT,
                        rule.getId(), type, rule.getName(),
                        describeAmount(rule, amount, base) + " recovered from the partner's share",
                        base, rule.getPartnerPercent(), rule.getRateAmount(),
                        amount.negate(), netDistributable, CalculationStep.PARTNER, null));
            }
        }

        BigDecimal partnerNetPayable = partnerShare.add(partnerAdjustments).setScale(scale, mode);

        // MINIMUM_PAYMENT runs last: it is a floor on the final figure, so
        // anything that would reduce the partner below it must already have
        // been applied.
        for (AgreementRule rule : in.rules()) {
            if (!"MINIMUM_PAYMENT".equals(rule.getRuleType())) continue;
            BigDecimal floor = computeAmount(rule, netDistributable, in.totalProduction(), scale, mode);
            if (floor.signum() > 0 && partnerNetPayable.compareTo(floor) < 0) {
                BigDecimal topUp = floor.subtract(partnerNetPayable);
                partnerAdjustments = partnerAdjustments.add(topUp);
                saicomexAdjustments = saicomexAdjustments.subtract(topUp);
                partnerNetPayable = floor;
                steps.add(new CalculationStep(++step, CalculationStep.STAGE_ADJUSTMENT,
                        rule.getId(), rule.getRuleType(), rule.getName(),
                        "Partner payable topped up to the contractual minimum of " + MONEY.format(floor),
                        floor.subtract(topUp), null, null, topUp, netDistributable,
                        CalculationStep.PARTNER, "SAIComex absorbs the shortfall of " + MONEY.format(topUp)));
                warnings.add("The minimum-payment rule topped the partner up by " + MONEY.format(topUp)
                        + "; SAIComex's share is reduced accordingly.");
            }
        }

        BigDecimal saicomexFinal = saicomexShare.add(saicomexAdjustments).setScale(scale, mode);

        steps.add(new CalculationStep(++step, CalculationStep.STAGE_TOTAL, null, null, "Partner net payable",
                MONEY.format(partnerShare) + " share "
                        + (partnerAdjustments.signum() >= 0 ? "+ " : "− ")
                        + MONEY.format(partnerAdjustments.abs()) + " adjustments = " + MONEY.format(partnerNetPayable),
                partnerShare, null, null, partnerNetPayable, netDistributable, CalculationStep.PARTNER, null));

        steps.add(new CalculationStep(++step, CalculationStep.STAGE_TOTAL, null, null, "SAIComex net position",
                MONEY.format(saicomexShare) + " share "
                        + (saicomexAdjustments.signum() >= 0 ? "+ " : "− ")
                        + MONEY.format(saicomexAdjustments.abs()) + " adjustments = " + MONEY.format(saicomexFinal),
                saicomexShare, null, null, saicomexFinal, netDistributable, CalculationStep.SAICOMEX, null));

        if (netDistributable.signum() < 0) {
            warnings.add("Costs exceeded revenue for this period — the distributable pool is negative ("
                    + MONEY.format(netDistributable) + "). Confirm how the shortfall is to be carried before approving.");
        }

        return new CalculationResult(gross, totalDeductions, netDistributable,
                saicomexShare, partnerShare, saicomexAdjustments, partnerAdjustments,
                partnerNetPayable, steps, warnings);
    }

    // ------------------------------------------------------------------ helpers

    /** What a cost-share rule matched, and how it lands. */
    private record CostOutcome(BigDecimal matchedTotal, BigDecimal amount,
                               BigDecimal saicomexPortion, BigDecimal partnerPortion,
                               String categories, String description) {}

    private CostOutcome applyCostRule(AgreementRule rule, CalculationInput in,
                                      Map<String, BigDecimal> costs, Set<String> consumed,
                                      int scale, RoundingMode mode) {
        Set<String> categories = categoriesFor(rule, in, consumed);
        if (categories.isEmpty()) return null;

        BigDecimal matched = categories.stream()
                .map(c -> nz(costs.get(c)))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(scale, mode);

        consumed.addAll(categories);
        if (matched.signum() == 0) return null;

        BigDecimal amount = matched;
        BigDecimal saicomexPortion;
        BigDecimal partnerPortion;

        switch (nvl(rule.getBorneBy(), "SHARED")) {
            case "SAICOMEX" -> { saicomexPortion = matched; partnerPortion = BigDecimal.ZERO.setScale(scale, mode); }
            case "PARTNER"  -> { partnerPortion = matched;  saicomexPortion = BigDecimal.ZERO.setScale(scale, mode); }
            default -> {
                BigDecimal sPct = nz(rule.getSaicomexPercent());
                BigDecimal pPct = nz(rule.getPartnerPercent());
                if (sPct.signum() == 0 && pPct.signum() == 0) {
                    // SHARED with no percentages means "share it the way the
                    // revenue is shared", which is what deducting from the pool
                    // achieves — so force that interpretation rather than
                    // guessing a split.
                    saicomexPortion = BigDecimal.ZERO.setScale(scale, mode);
                    partnerPortion = BigDecimal.ZERO.setScale(scale, mode);
                    return new CostOutcome(matched, matched, saicomexPortion, partnerPortion,
                            String.join(", ", categories),
                            MONEY.format(matched) + " of shared costs");
                }
                saicomexPortion = percentOf(matched, sPct, scale, mode);
                partnerPortion = matched.subtract(saicomexPortion).setScale(scale, mode);
            }
        }

        return new CostOutcome(matched, amount, saicomexPortion, partnerPortion,
                String.join(", ", categories),
                MONEY.format(matched) + " of " + rule.getName());
    }

    private Set<String> categoriesFor(AgreementRule rule, CalculationInput in, Set<String> consumed) {
        Set<String> result = new LinkedHashSet<>();

        if ("EXPENSE_CATEGORY".equals(rule.getScope()) && rule.getScopeValue() != null) {
            // An explicitly scoped rule takes its category even if an earlier
            // rule already consumed it — the operator asked for it by name.
            result.add(rule.getScopeValue());
            return result;
        }

        Set<String> candidates;
        if ("CAPEX_SHARE".equals(rule.getRuleType())) {
            candidates = new LinkedHashSet<>(in.capexCategoryCodes());
        } else if ("OPEX_SHARE".equals(rule.getRuleType())) {
            candidates = new LinkedHashSet<>(in.costsByCategoryCode().keySet());
            candidates.removeAll(in.capexCategoryCodes());
        } else {
            candidates = new LinkedHashSet<>(
                    DEFAULT_CATEGORIES.getOrDefault(rule.getRuleType(), Set.of()));
            candidates.retainAll(in.costsByCategoryCode().keySet());
        }

        for (String code : candidates) {
            if (!consumed.contains(code)) result.add(code);
        }
        return result;
    }

    /** Amount produced by a non-cost rule, from its calculation method. */
    private BigDecimal computeAmount(AgreementRule rule, BigDecimal base,
                                     BigDecimal production, int scale, RoundingMode mode) {
        return switch (nvl(rule.getCalculationMethod(), "PERCENTAGE")) {
            case "FIXED_AMOUNT" -> nz(rule.getFixedAmount()).setScale(scale, mode);
            case "RATE_PER_UNIT" -> nz(rule.getRateAmount())
                    .multiply(nz(production)).setScale(scale, mode);
            case "FULL_AMOUNT" -> base.setScale(scale, mode);
            default -> percentOf(base, nz(rule.getSaicomexPercent()), scale, mode);
        };
    }

    /**
     * Applies min/max/cap guard rails, and for CAPITAL_RECOVERY stops once the
     * advanced capital has been repaid — without which a recovery rule keeps
     * deducting for the life of the contract.
     */
    private BigDecimal applyBounds(AgreementRule rule, BigDecimal amount, BigDecimal base,
                                   int scale, RoundingMode mode) {
        BigDecimal result = amount;

        if (rule.getCapPercent() != null) {
            BigDecimal cap = percentOf(base, rule.getCapPercent(), scale, mode);
            if (result.compareTo(cap) > 0) result = cap;
        }
        if (rule.getMinAmount() != null && result.compareTo(rule.getMinAmount()) < 0) {
            result = rule.getMinAmount().setScale(scale, mode);
        }
        if (rule.getMaxAmount() != null && result.compareTo(rule.getMaxAmount()) > 0) {
            result = rule.getMaxAmount().setScale(scale, mode);
        }
        if ("CAPITAL_RECOVERY".equals(rule.getRuleType()) && rule.getRecoverableTotal() != null) {
            BigDecimal remaining = rule.getRecoverableTotal()
                    .subtract(nz(rule.getRecoveredToDate()))
                    .max(BigDecimal.ZERO)
                    .setScale(scale, mode);
            if (result.compareTo(remaining) > 0) result = remaining;
        }
        // Never let a deduction exceed what is left in the pool: a negative
        // pool created by a fee, rather than by real costs, is a bug wearing a
        // settlement's clothes.
        if (base.signum() > 0 && result.compareTo(base) > 0) result = base;

        return result.max(BigDecimal.ZERO).setScale(scale, mode);
    }

    private AgreementRule findAllocationRule(CalculationInput in) {
        String wanted = switch (nvl(in.agreement().getSettlementBasis(), "NET_REVENUE")) {
            case "PRODUCTION" -> "PRODUCTION_SHARE";
            case "PROFIT" -> "PROFIT_SHARE";
            default -> "REVENUE_SHARE";
        };
        return in.rules().stream()
                .filter(r -> wanted.equals(r.getRuleType()))
                .findFirst()
                // A REVENUE_SHARE rule is an acceptable stand-in for any basis:
                // it is the split the parties agreed, whatever it is applied to.
                .or(() -> in.rules().stream()
                        .filter(r -> "REVENUE_SHARE".equals(r.getRuleType()))
                        .findFirst())
                .orElse(null);
    }

    private BigDecimal basisValueForTier(CalculationInput in, BigDecimal netDistributable) {
        return "PRODUCTION".equals(nvl(in.agreement().getSettlementBasis(), "NET_REVENUE"))
                ? nz(in.totalProduction())
                : netDistributable;
    }

    private AgreementRuleTier resolveTier(List<AgreementRuleTier> tiers, BigDecimal value) {
        if (tiers == null) return null;
        return tiers.stream()
                .filter(t -> value.compareTo(nz(t.getFromValue())) >= 0)
                .filter(t -> t.getToValue() == null || value.compareTo(t.getToValue()) <= 0)
                .findFirst()
                .orElse(null);
    }

    private String describeAmount(AgreementRule rule, BigDecimal amount, BigDecimal base) {
        return switch (nvl(rule.getCalculationMethod(), "PERCENTAGE")) {
            case "FIXED_AMOUNT"  -> "Fixed " + MONEY.format(amount);
            case "RATE_PER_UNIT" -> MONEY.format(nz(rule.getRateAmount())) + " per "
                                    + nvl(rule.getRateUnit(), "unit") + " = " + MONEY.format(amount);
            case "FULL_AMOUNT"   -> "Full amount " + MONEY.format(amount);
            default -> MONEY.format(base) + " × " + PERCENT.format(nz(rule.getSaicomexPercent()))
                       + "% = " + MONEY.format(amount);
        };
    }

    private static BigDecimal percentOf(BigDecimal base, BigDecimal percent, int scale, RoundingMode mode) {
        return base.multiply(percent)
                   .divide(BigDecimal.valueOf(100), scale, mode);
    }

    private static boolean isCostShare(String ruleType) {
        return ruleType != null && (ruleType.endsWith("_COST_SHARE")
                || "OPEX_SHARE".equals(ruleType) || "CAPEX_SHARE".equals(ruleType));
    }

    private static boolean isRetention(String ruleType) {
        return SAICOMEX_RETENTIONS.contains(ruleType) || "SPECIAL_DEDUCTION".equals(ruleType);
    }

    private static RoundingMode roundingMode(String name) {
        try {
            return name == null ? RoundingMode.HALF_UP : RoundingMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return RoundingMode.HALF_UP;
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nvl(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
