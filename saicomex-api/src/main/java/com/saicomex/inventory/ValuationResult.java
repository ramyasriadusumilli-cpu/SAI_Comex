package com.saicomex.inventory;

import java.math.BigDecimal;

/**
 * The outcome of applying one stock movement to a running position:
 * the new on-hand quantity, the new weighted-average unit cost, and the
 * monetary value of the movement itself (a receipt's spend, or an issue's
 * cost valued at the average).
 */
public record ValuationResult(
        BigDecimal newQuantity,
        BigDecimal newAverageCost,
        BigDecimal movementValue) {
}
