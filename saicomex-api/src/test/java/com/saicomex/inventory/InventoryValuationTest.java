package com.saicomex.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stock valuation is a pure function of (current position, movement), so it is
 * tested directly rather than through a Spring context — the same posture as
 * {@code CommercialCalculationEngineTest}. If the weighted-average maths here
 * is wrong, every stock value and every issue cost in the system is wrong.
 */
class InventoryValuationTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("Receipt into empty stock sets the average cost to the receipt's unit cost")
    void receiptIntoEmptyStock() {
        ValuationResult r = InventoryValuation.apply(bd("0"), bd("0"), bd("100"), bd("12.50"));

        assertThat(r.newQuantity()).isEqualByComparingTo("100");
        assertThat(r.newAverageCost()).isEqualByComparingTo("12.50");
        assertThat(r.movementValue()).isEqualByComparingTo("1250.00");
    }

    @Test
    @DisplayName("A second receipt weights the average cost across old and new stock")
    void secondReceiptWeightsAverage() {
        // 100 @ 12.50 already on hand, receive 100 @ 15.00
        // (100*12.50 + 100*15.00) / 200 = 2750 / 200 = 13.75
        ValuationResult r = InventoryValuation.apply(bd("100"), bd("12.50"), bd("100"), bd("15.00"));

        assertThat(r.newQuantity()).isEqualByComparingTo("200");
        assertThat(r.newAverageCost()).isEqualByComparingTo("13.75");
        assertThat(r.movementValue()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("An issue reduces quantity, leaves the average untouched, and is valued at that average")
    void issueValuedAtCurrentAverage() {
        // 200 @ 13.75 on hand, issue 50 out
        ValuationResult r = InventoryValuation.apply(bd("200"), bd("13.75"), bd("-50"), null);

        assertThat(r.newQuantity()).isEqualByComparingTo("150");
        assertThat(r.newAverageCost()).isEqualByComparingTo("13.75");
        assertThat(r.movementValue()).isEqualByComparingTo("687.50"); // 50 * 13.75
    }

    @Test
    @DisplayName("An issue is valued at the running average, never at the last receipt's cost")
    void issueIgnoresLastReceiptCost() {
        // Two receipts: 100 @ 10, then 100 @ 20 -> avg 15. Issue 100 -> valued 1500, not 2000.
        ValuationResult afterFirst  = InventoryValuation.apply(bd("0"), bd("0"), bd("100"), bd("10"));
        ValuationResult afterSecond = InventoryValuation.apply(
                afterFirst.newQuantity(), afterFirst.newAverageCost(), bd("100"), bd("20"));

        ValuationResult issue = InventoryValuation.apply(
                afterSecond.newQuantity(), afterSecond.newAverageCost(), bd("-100"), null);

        assertThat(afterSecond.newAverageCost()).isEqualByComparingTo("15");
        assertThat(issue.movementValue()).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("Driving stock below zero is rejected — you cannot issue more than is on hand")
    void issueBelowZeroRejected() {
        assertThatThrownBy(() -> InventoryValuation.apply(bd("10"), bd("5"), bd("-25"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negative");
    }
}
