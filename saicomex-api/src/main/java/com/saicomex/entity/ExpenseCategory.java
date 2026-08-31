package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §15 — configurable, hierarchical expense category. {@code expenseClass}
 * (OPEX/CAPEX) feeds the agreement engine, where capex/opex share are
 * separate contractual parameters (SRS §11).
 */
@Entity
@Table(name = "expense_categories")
@Getter
@Setter
public class ExpenseCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    /** OPEX | CAPEX */
    @Column(name = "expense_class", nullable = false, length = 20)
    private String expenseClass = "OPEX";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 100;

    @Column(columnDefinition = "TEXT")
    private String description;
}
