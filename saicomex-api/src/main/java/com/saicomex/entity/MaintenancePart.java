package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §22 — a part used on a maintenance job. No audit columns; lives and dies
 * with its record (cascade). May reference an inventory item or be free text.
 */
@Entity
@Table(name = "maintenance_parts")
@Getter
@Setter
public class MaintenancePart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "maintenance_record_id", nullable = false)
    private Long maintenanceRecordId;

    @Column(name = "item_id")
    private Long itemId;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 18, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 18, scale = 4)
    private BigDecimal totalCost;
}
