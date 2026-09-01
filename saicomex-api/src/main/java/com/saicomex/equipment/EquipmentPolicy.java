package com.saicomex.equipment;

import com.saicomex.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Equipment allocation and maintenance rules (SRS §20-22) — pure, so the money
 * rollup and the allocation/hours guards are proven without a database. The
 * services compute costs and drive the allocation-history transition through here.
 */
public final class EquipmentPolicy {

    private EquipmentPolicy() {
    }

    /** Sum of the maintenance part line totals. */
    public static BigDecimal partsCost(List<BigDecimal> lineTotals) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : lineTotals) {
            if (v != null) sum = sum.add(v);
        }
        return sum;
    }

    /** A maintenance job's total: parts + labour + other, nulls as zero. */
    public static BigDecimal maintenanceTotal(BigDecimal parts, BigDecimal labour, BigDecimal other) {
        return nz(parts).add(nz(labour)).add(nz(other));
    }

    /**
     * @throws BusinessRuleException if a new placement would start before the
     *                               current placement it supersedes
     */
    public static void validateReallocation(LocalDate currentFrom, LocalDate newFrom) {
        if (currentFrom != null && newFrom != null && newFrom.isBefore(currentFrom)) {
            throw new BusinessRuleException(
                    "A re-allocation cannot start (" + newFrom + ") before the current placement (" + currentFrom + ")");
        }
    }

    /**
     * @throws BusinessRuleException if closing operating hours are below opening hours
     */
    public static void validateHours(BigDecimal opening, BigDecimal closing) {
        if (opening != null && closing != null && closing.compareTo(opening) < 0) {
            throw new BusinessRuleException(
                    "Closing hours (" + closing + ") cannot be below opening hours (" + opening + ")");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
