package com.saicomex.equipment;

import com.saicomex.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SRS §20-22 — equipment and maintenance rules that must hold regardless of
 * storage: a maintenance job's total is parts + labour + other, a re-allocation
 * cannot start before the placement it supersedes, and closing hours cannot be
 * below opening hours. Pure, tested in isolation.
 */
class EquipmentPolicyTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }

    @Test
    @DisplayName("Parts cost is the sum of the part line totals")
    void partsCostSums() {
        assertThat(EquipmentPolicy.partsCost(List.of(bd("10"), bd("20.50"), bd("5"))))
                .isEqualByComparingTo("35.50");
    }

    @Test
    @DisplayName("Maintenance total is parts + labour + other, treating nulls as zero")
    void maintenanceTotal() {
        assertThat(EquipmentPolicy.maintenanceTotal(bd("100"), bd("50"), bd("25"))).isEqualByComparingTo("175");
        assertThat(EquipmentPolicy.maintenanceTotal(bd("100"), null, null)).isEqualByComparingTo("100");
        assertThat(EquipmentPolicy.maintenanceTotal(null, null, null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("A re-allocation may start on or after the current placement's start")
    void reallocationForwardOk() {
        assertThatCode(() -> EquipmentPolicy.validateReallocation(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A re-allocation cannot start before the placement it supersedes")
    void reallocationBackwardsRefused() {
        assertThatThrownBy(() -> EquipmentPolicy.validateReallocation(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("before");
    }

    @Test
    @DisplayName("Closing hours below opening hours are refused")
    void hoursMustNotGoBackwards() {
        assertThatThrownBy(() -> EquipmentPolicy.validateHours(bd("150"), bd("100")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> EquipmentPolicy.validateHours(bd("100"), bd("150"))).doesNotThrowAnyException();
        assertThatCode(() -> EquipmentPolicy.validateHours(null, bd("150"))).doesNotThrowAnyException();
        assertThatCode(() -> EquipmentPolicy.validateHours(bd("100"), null)).doesNotThrowAnyException();
    }
}
