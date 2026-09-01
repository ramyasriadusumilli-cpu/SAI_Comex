package com.saicomex.inventory;

import com.saicomex.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SRS §19 — goods receipt against a purchase order. The rules that must not
 * bend: a line total is quantity x unit cost, you cannot receive a
 * non-positive quantity, and you can never receive more than was ordered.
 * Pure, tested in isolation.
 */
class PurchaseOrderPolicyTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    @DisplayName("A line total is quantity times unit cost")
    void lineTotal() {
        assertThat(PurchaseOrderPolicy.lineTotal(bd("10"), bd("12.50"))).isEqualByComparingTo("125.00");
    }

    @Test
    @DisplayName("Receiving up to the ordered quantity is allowed")
    void receiveWithinOrder() {
        assertThatCode(() -> PurchaseOrderPolicy.validateReceive(bd("100"), bd("60"), bd("40")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Receiving more than the outstanding quantity is refused")
    void overReceiptRefused() {
        assertThatThrownBy(() -> PurchaseOrderPolicy.validateReceive(bd("100"), bd("60"), bd("50")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("more than");
    }

    @Test
    @DisplayName("Receiving a non-positive quantity is refused")
    void nonPositiveReceiptRefused() {
        assertThatThrownBy(() -> PurchaseOrderPolicy.validateReceive(bd("100"), bd("0"), bd("0")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PurchaseOrderPolicy.validateReceive(bd("100"), bd("0"), bd("-5")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Status becomes RECEIVED when every line is fully received")
    void statusFullyReceived() {
        assertThat(PurchaseOrderPolicy.statusAfterReceipt(true, true)).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("Status becomes PARTIALLY_RECEIVED when some but not all is received")
    void statusPartiallyReceived() {
        assertThat(PurchaseOrderPolicy.statusAfterReceipt(false, true)).isEqualTo("PARTIALLY_RECEIVED");
    }
}
