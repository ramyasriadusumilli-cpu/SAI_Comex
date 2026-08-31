package com.saicomex.inventory;

import com.saicomex.exception.BusinessRuleException;

/**
 * Licence-controlled item issue policy (SRS §18). Explosives, detonators and
 * any item flagged {@code is_controlled} or {@code requires_permit} may only be
 * issued against a permit reference, to a named recipient, out of a magazine
 * store. A pure guard, so the rule is proven in isolation and the service layer
 * only has to call it.
 */
public final class ControlledItemPolicy {

    private ControlledItemPolicy() {
    }

    /**
     * @throws BusinessRuleException if the issue of a controlled item is missing
     *                               its permit reference, recipient, or magazine store
     */
    public static void validateIssue(
            boolean isControlled, boolean requiresPermit,
            String permitReference, String recipientName, Long recipientEmployeeId, String storeType) {

        if (!isControlled && !requiresPermit) {
            return;
        }
        if (permitReference == null || permitReference.isBlank()) {
            throw new BusinessRuleException("A permit reference is required to issue a controlled item.");
        }
        boolean hasRecipient = recipientEmployeeId != null
                || (recipientName != null && !recipientName.isBlank());
        if (!hasRecipient) {
            throw new BusinessRuleException("An authorised recipient is required to issue a controlled item.");
        }
        if (!"MAGAZINE".equalsIgnoreCase(storeType)) {
            throw new BusinessRuleException("A controlled item may only be issued from a magazine store.");
        }
    }
}
