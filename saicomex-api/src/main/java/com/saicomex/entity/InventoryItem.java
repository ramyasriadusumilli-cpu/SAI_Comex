package com.saicomex.entity;

import com.saicomex.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §18, §19 — an inventory item master row: fuel, explosives, consumables,
 * spares, PPE, chemicals. Has created/updated audit columns but no soft-delete
 * (items are deactivated via {@code isActive}, never removed), so it extends
 * {@link BaseEntity} rather than {@code SoftDeletableEntity}.
 */
@Entity
@Table(name = "inventory_items")
@Getter
@Setter
public class InventoryItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 40, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    // FUEL | EXPLOSIVE | CONSUMABLE | SPARE | PPE | CHEMICAL | OTHER
    @Column(name = "item_type", nullable = false, length = 30)
    private String itemType;

    @Column(name = "category_id")
    private Long categoryId;

    // litre | kg | each | box
    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "is_controlled", nullable = false)
    private Boolean isControlled = false;

    @Column(name = "requires_permit", nullable = false)
    private Boolean requiresPermit = false;

    @Column(name = "minimum_stock", precision = 18, scale = 4)
    private BigDecimal minimumStock;

    @Column(name = "maximum_stock", precision = 18, scale = 4)
    private BigDecimal maximumStock;

    @Column(name = "reorder_level", precision = 18, scale = 4)
    private BigDecimal reorderLevel;

    @Column(name = "standard_cost", precision = 18, scale = 6)
    private BigDecimal standardCost;

    @Column(name = "cost_currency", length = 3, columnDefinition = "char")
    private String costCurrency;

    // WEIGHTED_AVG | FIFO
    @Column(name = "valuation_method", nullable = false, length = 20)
    private String valuationMethod = "WEIGHTED_AVG";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
