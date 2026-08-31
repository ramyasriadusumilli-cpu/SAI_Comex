package com.saicomex.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Weighted-average stock valuation — a pure function, no Spring, no database,
 * the same posture as the commercial calculation engine. Every stock value and
 * every issue cost in the platform flows through here, so it is unit-tested
 * directly against worked numbers.
 *
 * <p>The rules (SRS §19):
 * <ul>
 *   <li>An inbound movement with a unit cost re-weights the average:
 *       {@code newAvg = (oldQty·oldAvg + inQty·unitCost) / (oldQty + inQty)}.</li>
 *   <li>An inbound movement without a cost (e.g. a stock return) is valued at
 *       the current average and leaves the average unchanged.</li>
 *   <li>An outbound movement leaves the average untouched and is valued at that
 *       average — never at the last receipt's price.</li>
 *   <li>No movement may drive on-hand quantity below zero.</li>
 * </ul>
 *
 * <p>Scales match the columns: quantity {@code NUMERIC(18,4)}, average cost
 * {@code NUMERIC(18,6)}, movement value {@code NUMERIC(18,4)}, all HALF_UP.
 */
public final class InventoryValuation {

    private static final int QTY_SCALE = 4;
    private static final int COST_SCALE = 6;
    private static final int VALUE_SCALE = 4;

    private InventoryValuation() {
    }

    /**
     * @param currentQty on-hand quantity before the movement
     * @param currentAvg weighted-average unit cost before the movement
     * @param signedQty  the movement quantity: positive = in, negative = out
     * @param unitCost   the movement's unit cost (used only for costed inbound movements); may be null
     */
    public static ValuationResult apply(
            BigDecimal currentQty, BigDecimal currentAvg, BigDecimal signedQty, BigDecimal unitCost) {

        BigDecimal newQty = currentQty.add(signedQty);
        if (newQty.signum() < 0) {
            throw new IllegalStateException(
                    "Movement would drive stock negative: on hand " + currentQty + ", movement " + signedQty);
        }

        if (signedQty.signum() > 0) {
            BigDecimal value;
            BigDecimal newAvg;
            if (unitCost != null) {
                value = signedQty.multiply(unitCost);
                BigDecimal pooledCost = currentQty.multiply(currentAvg).add(value);
                newAvg = newQty.signum() == 0 ? currentAvg : pooledCost.divide(newQty, COST_SCALE, RoundingMode.HALF_UP);
            } else {
                value = signedQty.multiply(currentAvg);
                newAvg = currentAvg;
            }
            return result(newQty, newAvg, value);
        }

        if (signedQty.signum() < 0) {
            BigDecimal value = signedQty.abs().multiply(currentAvg);
            return result(newQty, currentAvg, value);
        }

        // Zero-quantity movement (e.g. a COUNT that confirms the existing figure).
        return result(currentQty, currentAvg, BigDecimal.ZERO);
    }

    private static ValuationResult result(BigDecimal qty, BigDecimal avg, BigDecimal value) {
        return new ValuationResult(
                qty.setScale(QTY_SCALE, RoundingMode.HALF_UP),
                avg.setScale(COST_SCALE, RoundingMode.HALF_UP),
                value.setScale(VALUE_SCALE, RoundingMode.HALF_UP));
    }
}
