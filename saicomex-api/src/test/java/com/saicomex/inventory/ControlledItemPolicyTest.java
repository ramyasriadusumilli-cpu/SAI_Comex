package com.saicomex.inventory;

import com.saicomex.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SRS §18 — issuing a licence-controlled item (explosives, detonators) is only
 * legal against a permit reference, to a named recipient, out of a magazine.
 * Pure policy, tested directly.
 */
class ControlledItemPolicyTest {

    @Test
    @DisplayName("An ordinary consumable issue needs no permit, recipient or magazine")
    void ordinaryItemIssueIsUnrestricted() {
        assertThatCode(() -> ControlledItemPolicy.validateIssue(
                false, false, null, null, null, "GENERAL"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A controlled-item issue with no permit reference is refused")
    void controlledItemWithoutPermitIsRefused() {
        assertThatThrownBy(() -> ControlledItemPolicy.validateIssue(
                true, true, "  ", "J. Banda", 5L, "MAGAZINE"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("permit");
    }

    @Test
    @DisplayName("A controlled-item issue with no recipient is refused")
    void controlledItemWithoutRecipientIsRefused() {
        assertThatThrownBy(() -> ControlledItemPolicy.validateIssue(
                true, true, "PERMIT-2026-0042", null, null, "MAGAZINE"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    @DisplayName("A controlled-item issue not from a magazine store is refused")
    void controlledItemFromNonMagazineIsRefused() {
        assertThatThrownBy(() -> ControlledItemPolicy.validateIssue(
                true, true, "PERMIT-2026-0042", "J. Banda", 5L, "GENERAL"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("magazine");
    }

    @Test
    @DisplayName("A controlled-item issue with permit, recipient and magazine passes")
    void controlledItemFullyDocumentedPasses() {
        assertThatCode(() -> ControlledItemPolicy.validateIssue(
                true, true, "PERMIT-2026-0042", "J. Banda", 5L, "MAGAZINE"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A recipient given only by employee id (no free-text name) still satisfies the recipient rule")
    void recipientByEmployeeIdAloneIsEnough() {
        assertThatCode(() -> ControlledItemPolicy.validateIssue(
                true, false, "PERMIT-2026-0042", null, 5L, "MAGAZINE"))
                .doesNotThrowAnyException();
    }
}
