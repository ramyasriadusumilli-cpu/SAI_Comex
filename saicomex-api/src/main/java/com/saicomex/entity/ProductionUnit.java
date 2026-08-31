package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §13 — a configurable production unit (grams / kg / tonnes / oz), with a
 * conversion factor to its class base unit so quantities recorded in
 * different units can be summed on one tile without guesswork.
 */
@Entity
@Table(name = "production_units")
@Getter
@Setter
public class ProductionUnit {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    /** MASS | VOLUME | COUNT */
    @Column(name = "unit_class", nullable = false, length = 20)
    private String unitClass;

    @Column(name = "base_factor", nullable = false, precision = 18, scale = 8)
    private BigDecimal baseFactor = BigDecimal.ONE;

    @Column(name = "decimal_places", nullable = false)
    private Short decimalPlaces = 2;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 100;
}
