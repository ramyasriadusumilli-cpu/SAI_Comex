package com.saicomex.engine;

import com.saicomex.entity.AgreementRule;
import com.saicomex.entity.AgreementRuleTier;
import com.saicomex.entity.CommercialAgreement;
import com.saicomex.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The engine is a pure function, so it is tested directly rather than through
 * a Spring context. The first test is the worked example printed in SRS §25 —
 * if that ever fails, the contract with the business has been broken.
 */
class CommercialCalculationEngineTest {

    private final CommercialCalculationEngine engine = new CommercialCalculationEngine();

    // ------------------------------------------------------------------ SRS §25

    @Test
    @DisplayName("SRS §25 worked example: 110,000 gross − 35,000 costs, split 70/30")
    void srsWorkedExample() {
        CommercialAgreement agreement = agreement(null, null);

        AgreementRule costs = rule(1L, "OPEX_SHARE", 10, "Operating costs", "PERCENTAGE");
        costs.setDeductBeforeSplit(true);

        AgreementRule split = rule(2L, "REVENUE_SHARE", 200, "Revenue split", "PERCENTAGE");
        split.setSaicomexPercent(bd("70"));
        split.setPartnerPercent(bd("30"));

        CalculationResult result = engine.calculate(input(
                agreement, List.of(costs, split),
                bd("110000"),
                Map.of("LABOUR", bd("20000"), "DIESEL", bd("15000"))));

        assertThat(result.grossRevenue()).isEqualByComparingTo("110000.00");
        assertThat(result.totalDeductions()).isEqualByComparingTo("35000.00");
        assertThat(result.netDistributable()).isEqualByComparingTo("75000.00");
        assertThat(result.saicomexShare()).isEqualByComparingTo("52500.00");
        assertThat(result.partnerShare()).isEqualByComparingTo("22500.00");
        assertThat(result.partnerNetPayable()).isEqualByComparingTo("22500.00");
        assertThat(result.warnings()).isEmpty();
    }

    // ------------------------------------------------------------------ SRS §11

    @Test
    @DisplayName("SRS §11: the same engine produces 80/20 and 60/40 with no code change")
    void differentContractsDifferentSplits() {
        Map<String, BigDecimal> costs = Map.of("LABOUR", bd("10000"));

        CalculationResult a = engine.calculate(input(agreement(null, null),
                List.of(deductAll(), split(bd("80"), bd("20"))),
                bd("100000"), costs));
        CalculationResult b = engine.calculate(input(agreement(null, null),
                List.of(deductAll(), split(bd("60"), bd("40"))),
                bd("100000"), costs));

        assertThat(a.saicomexShare()).isEqualByComparingTo("72000.00");
        assertThat(a.partnerShare()).isEqualByComparingTo("18000.00");
        assertThat(b.saicomexShare()).isEqualByComparingTo("54000.00");
        assertThat(b.partnerShare()).isEqualByComparingTo("36000.00");
    }

    @Test
    @DisplayName("A cost may be shared on different terms from the revenue")
    void costSharedDifferentlyFromRevenue() {
        // Revenue 70/30, but diesel is split 50/50 after the allocation.
        AgreementRule fuel = rule(1L, "FUEL_COST_SHARE", 10, "Diesel 50/50", "PERCENTAGE");
        fuel.setDeductBeforeSplit(false);
        fuel.setBorneBy("SHARED");
        fuel.setSaicomexPercent(bd("50"));
        fuel.setPartnerPercent(bd("50"));

        AgreementRule other = deductAll();
        other.setSequenceNo(20);

        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(fuel, other, split(bd("70"), bd("30"))),
                bd("100000"),
                Map.of("DIESEL", bd("20000"), "LABOUR", bd("10000"))));

        // Diesel stays out of the pool; only labour is deducted before the split.
        assertThat(r.totalDeductions()).isEqualByComparingTo("10000.00");
        assertThat(r.netDistributable()).isEqualByComparingTo("90000.00");
        assertThat(r.saicomexShare()).isEqualByComparingTo("63000.00");
        assertThat(r.partnerShare()).isEqualByComparingTo("27000.00");
        // Each side then carries 10,000 of diesel.
        assertThat(r.partnerAdjustments()).isEqualByComparingTo("-10000.00");
        assertThat(r.partnerNetPayable()).isEqualByComparingTo("17000.00");
    }

    @Test
    @DisplayName("A specific cost rule consumes its categories before a general one sees them")
    void specificRuleConsumesCategoriesFirst() {
        AgreementRule fuel = rule(1L, "FUEL_COST_SHARE", 10, "Diesel to partner", "PERCENTAGE");
        fuel.setDeductBeforeSplit(false);
        fuel.setBorneBy("PARTNER");

        AgreementRule opex = deductAll();
        opex.setSequenceNo(20);

        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(fuel, opex, split(bd("70"), bd("30"))),
                bd("100000"),
                Map.of("DIESEL", bd("20000"), "LABOUR", bd("10000"))));

        // OPEX_SHARE must not deduct the diesel a second time.
        assertThat(r.totalDeductions()).isEqualByComparingTo("10000.00");
        assertThat(r.partnerAdjustments()).isEqualByComparingTo("-20000.00");
        assertThat(r.partnerNetPayable()).isEqualByComparingTo("7000.00");
    }

    @Test
    @DisplayName("Expenditure no rule covers is reported, not silently deducted or dropped")
    void unclaimedExpenditureWarns() {
        AgreementRule fuel = rule(1L, "FUEL_COST_SHARE", 10, "Fuel", "PERCENTAGE");
        fuel.setDeductBeforeSplit(true);

        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(fuel, split(bd("70"), bd("30"))),
                bd("100000"),
                Map.of("DIESEL", bd("5000"), "SECURITY", bd("8000"))));

        assertThat(r.totalDeductions()).isEqualByComparingTo("5000.00");
        assertThat(r.warnings()).anySatisfy(w -> {
            assertThat(w).contains("8,000.00");
            assertThat(w).contains("SECURITY");
        });
    }

    // ------------------------------------------------------------------ fees

    @Test
    @DisplayName("A management fee leaves the pool and is credited to SAIComex")
    void managementFeeIsRetained() {
        AgreementRule fee = rule(1L, "MANAGEMENT_FEE", 90, "Management fee 10%", "PERCENTAGE");
        fee.setSaicomexPercent(bd("10"));

        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(fee, split(bd("50"), bd("50"))),
                bd("100000"), Map.of()));

        assertThat(r.totalDeductions()).isEqualByComparingTo("10000.00");
        assertThat(r.netDistributable()).isEqualByComparingTo("90000.00");
        assertThat(r.partnerShare()).isEqualByComparingTo("45000.00");
        // SAIComex: 45,000 of the split plus the 10,000 fee.
        assertThat(r.saicomexShare().add(r.saicomexAdjustments())).isEqualByComparingTo("55000.00");
    }

    @Test
    @DisplayName("Capital recovery stops once the advance is repaid")
    void capitalRecoveryStopsAtRemainingBalance() {
        AgreementRule recovery = rule(1L, "CAPITAL_RECOVERY", 100, "Capital recovery", "FIXED_AMOUNT");
        recovery.setFixedAmount(bd("15000"));
        recovery.setRecoverableTotal(bd("50000"));
        recovery.setRecoveredToDate(bd("42000"));   // only 8,000 left to recover

        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(recovery, split(bd("70"), bd("30"))),
                bd("100000"), Map.of()));

        assertThat(r.totalDeductions()).isEqualByComparingTo("8000.00");
        assertThat(r.netDistributable()).isEqualByComparingTo("92000.00");
    }

    @Test
    @DisplayName("A minimum payment tops the partner up and SAIComex absorbs the shortfall")
    void minimumPaymentFloor() {
        AgreementRule minimum = rule(1L, "MINIMUM_PAYMENT", 300, "Minimum monthly payment", "FIXED_AMOUNT");
        minimum.setFixedAmount(bd("5000"));

        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(deductAll(), minimum, split(bd("90"), bd("10"))),
                bd("30000"),
                Map.of("LABOUR", bd("10000"))));

        // 20,000 distributable → partner would get 2,000; floor lifts it to 5,000.
        assertThat(r.partnerNetPayable()).isEqualByComparingTo("5000.00");
        assertThat(r.saicomexAdjustments()).isEqualByComparingTo("-3000.00");
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w).contains("minimum-payment"));
    }

    // ------------------------------------------------------------------ tiers

    @Test
    @DisplayName("A tiered split picks the band the period's value falls in")
    void tieredSplit() {
        AgreementRule tiered = rule(1L, "REVENUE_SHARE", 200, "Tiered split", "TIERED");

        AgreementRuleTier low = tier(1, bd("0"), bd("50000"), bd("60"), bd("40"));
        AgreementRuleTier high = tier(2, bd("50000.01"), null, bd("75"), bd("25"));

        CalculationInput in = new CalculationInput(
                agreement(null, null), List.of(tiered),
                Map.of(1L, List.of(low, high)),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "USD",
                bd("80000"), Map.of(), Set.of(), bd("1200"), "G");

        CalculationResult r = engine.calculate(in);

        assertThat(r.saicomexShare()).isEqualByComparingTo("60000.00");
        assertThat(r.partnerShare()).isEqualByComparingTo("20000.00");
    }

    // ------------------------------------------------------------------ guards

    @Test
    @DisplayName("An agreement with no split at all is refused rather than guessed")
    void missingSplitIsRefused() {
        assertThatThrownBy(() -> engine.calculate(input(
                agreement(null, null), List.of(deductAll()), bd("100000"), Map.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no allocation rule");
    }

    @Test
    @DisplayName("A split that does not total 100% is refused")
    void invalidSplitIsRefused() {
        assertThatThrownBy(() -> engine.calculate(input(
                agreement(null, null), List.of(split(bd("70"), bd("20"))), bd("100000"), Map.of())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not 100%");
    }

    @Test
    @DisplayName("The two shares always reconstitute the pool exactly, with no orphan cent")
    void roundingNeverLosesACent() {
        // 100,000.01 at 33.333333% does not divide cleanly.
        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(split(bd("33.333333"), bd("66.666667"))),
                bd("100000.01"), Map.of()));

        assertThat(r.saicomexShare().add(r.partnerShare()))
                .isEqualByComparingTo(r.netDistributable());
    }

    @Test
    @DisplayName("The agreement's default split is used when no allocation rule exists")
    void defaultSplitFallback() {
        CalculationResult r = engine.calculate(input(
                agreement(bd("65"), bd("35")), List.of(deductAll()), bd("100000"),
                Map.of("LABOUR", bd("20000"))));

        assertThat(r.saicomexShare()).isEqualByComparingTo("52000.00");
        assertThat(r.partnerShare()).isEqualByComparingTo("28000.00");
    }

    @Test
    @DisplayName("Every step of the waterfall is recorded for the audit trail")
    void everyStepIsRecorded() {
        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(deductAll(), split(bd("70"), bd("30"))),
                bd("110000"), Map.of("LABOUR", bd("35000"))));

        assertThat(r.steps()).isNotEmpty();
        assertThat(r.steps()).extracting(CalculationStep::stepNo)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, r.steps().size()).boxed().toList());
        assertThat(r.steps()).anySatisfy(s ->
                assertThat(s.expression()).contains("70").contains("52,500.00"));
        assertThat(r.steps()).allSatisfy(s -> assertThat(s.expression()).isNotBlank());
    }

    @Test
    @DisplayName("A period where costs exceed revenue warns rather than quietly producing a negative payout")
    void negativePoolWarns() {
        CalculationResult r = engine.calculate(input(agreement(null, null),
                List.of(deductAll(), split(bd("70"), bd("30"))),
                bd("10000"), Map.of("LABOUR", bd("25000"))));

        assertThat(r.netDistributable()).isEqualByComparingTo("-15000.00");
        assertThat(r.warnings()).anySatisfy(w -> assertThat(w).contains("negative"));
    }

    // ------------------------------------------------------------------ fixtures

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private CommercialAgreement agreement(BigDecimal defaultSaicomex, BigDecimal defaultPartner) {
        CommercialAgreement a = new CommercialAgreement();
        a.setId(1L);
        a.setName("Test agreement");
        a.setSettlementBasis("NET_REVENUE");
        a.setCurrency("USD");
        a.setRoundingScale((short) 2);
        a.setRoundingMode("HALF_UP");
        a.setDefaultSaicomexPercent(defaultSaicomex);
        a.setDefaultPartnerPercent(defaultPartner);
        return a;
    }

    private AgreementRule rule(Long id, String type, int seq, String name, String method) {
        AgreementRule r = new AgreementRule();
        r.setId(id);
        r.setAgreementId(1L);
        r.setRuleType(type);
        r.setSequenceNo(seq);
        r.setName(name);
        r.setCalculationMethod(method);
        r.setScope("ALL");
        r.setBorneBy("SHARED");
        r.setDeductBeforeSplit(false);
        r.setIsActive(true);
        return r;
    }

    /** An OPEX_SHARE rule that takes every operating cost off the pool. */
    private AgreementRule deductAll() {
        AgreementRule r = rule(9L, "OPEX_SHARE", 10, "All operating costs", "PERCENTAGE");
        r.setDeductBeforeSplit(true);
        return r;
    }

    private AgreementRule split(BigDecimal saicomex, BigDecimal partner) {
        AgreementRule r = rule(10L, "REVENUE_SHARE", 200, "Revenue split", "PERCENTAGE");
        r.setSaicomexPercent(saicomex);
        r.setPartnerPercent(partner);
        return r;
    }

    private AgreementRuleTier tier(int no, BigDecimal from, BigDecimal to,
                                   BigDecimal saicomex, BigDecimal partner) {
        AgreementRuleTier t = new AgreementRuleTier();
        t.setTierNo(no);
        t.setFromValue(from);
        t.setToValue(to);
        t.setSaicomexPercent(saicomex);
        t.setPartnerPercent(partner);
        return t;
    }

    private CalculationInput input(CommercialAgreement agreement, List<AgreementRule> rules,
                                   BigDecimal gross, Map<String, BigDecimal> costs) {
        return new CalculationInput(agreement, rules, Map.of(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "USD",
                gross, costs, Set.of(), bd("1000"), "G");
    }
}
