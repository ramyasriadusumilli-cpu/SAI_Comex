package com.saicomex.inventory;

import com.saicomex.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Purchase-order and goods-receipt rules (SRS §19) — pure, so the correctness
 * of the money and the over-receipt guard is proven without a database. The
 * service layer computes totals and posts stock through here.
 */
public final class PurchaseOrderPolicy {

    private PurchaseOrderPolicy() {
    }

    /** A line's total: ordered quantity x unit cost, at the line_total column's scale. */
    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal q = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal c = unitCost == null ? BigDecimal.ZERO : unitCost;
        return q.multiply(c).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * @throws BusinessRuleException if the receipt is non-positive, or would take
     *                               the received quantity past what was ordered
     */
    public static void validateReceive(BigDecimal ordered, BigDecimal alreadyReceived, BigDecimal receiveNow) {
        if (receiveNow == null || receiveNow.signum() <= 0) {
            throw new BusinessRuleException("Receipt quantity must be positive.");
        }
        BigDecimal received = alreadyReceived == null ? BigDecimal.ZERO : alreadyReceived;
        if (received.add(receiveNow).compareTo(ordered) > 0) {
            throw new BusinessRuleException(
                    "Cannot receive more than the ordered quantity: ordered " + ordered
                            + ", already received " + received + ", receiving " + receiveNow);
        }
    }

    /** The order's status after a receipt: fully received closes it, otherwise it is partial. */
    public static String statusAfterReceipt(boolean allFullyReceived, boolean anyReceived) {
        if (allFullyReceived) {
            return "RECEIVED";
        }
        return "PARTIALLY_RECEIVED";
    }
}
