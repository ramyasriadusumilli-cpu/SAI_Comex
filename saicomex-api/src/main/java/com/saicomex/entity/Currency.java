package com.saicomex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §40 — reference currency table. Natural key ({@code code}), no audit
 * columns: this is a small, admin-maintained lookup table, not a business
 * record.
 */
@Entity
@Table(name = "currencies")
@Getter
@Setter
public class Currency {

    @Id
    @Column(name = "code", length = 3)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 8)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private Short decimalPlaces = 2;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 100;
}
