package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §11 — lookup of configurable agreement parameters, so SAIComex can add
 * one later "without requiring major application redevelopment".
 */
@Entity
@Table(name = "agreement_rule_types")
@Getter
@Setter
public class AgreementRuleType {

    @Id
    @Column(name = "code", length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Where the rule acts in the SRS §12 waterfall: DEDUCTION | ALLOCATION | ADJUSTMENT */
    @Column(nullable = false, length = 20)
    private String stage;

    @Column(name = "default_sequence", nullable = false)
    private Integer defaultSequence = 100;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
