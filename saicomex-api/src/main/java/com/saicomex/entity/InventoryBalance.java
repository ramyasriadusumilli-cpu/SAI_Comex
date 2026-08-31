package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SRS §19 — the running stock position for one item in one store: on-hand
 * quantity and weighted-average unit cost. Maintained by the service layer in
 * the same transaction as the movement that changes it, never recomputed from
 * scratch. Composite key (item, store).
 */
@Entity
@Table(name = "inventory_balances")
@Getter
@Setter
public class InventoryBalance {

    @EmbeddedId
    private InventoryBalanceId id;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "average_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal averageCost = BigDecimal.ZERO;

    @Column(name = "cost_currency", length = 3, columnDefinition = "char")
    private String costCurrency;

    @Column(name = "last_movement_at")
    private LocalDateTime lastMovementAt;
}
