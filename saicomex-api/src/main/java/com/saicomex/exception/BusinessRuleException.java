package com.saicomex.exception;

/**
 * A request that is syntactically valid but breaks a business rule — for
 * example calculating a settlement for a shaft with no active contract.
 * Surfaces as 422 with the message shown to the operator verbatim, so the
 * message must read as something an operator can act on.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
