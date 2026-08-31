package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §11 — one tier of a TIERED {@link AgreementRule}, e.g. "first 500g at
 * 70/30, above that 60/40".
 */
@Entity
@Table(name = "agreement_rule_tiers")
@Getter
@Setter
public class AgreementRuleTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "tier_no", nullable = false)
    private Integer tierNo;

    @Column(name = "from_value", nullable = false, precision = 18, scale = 4)
    private BigDecimal fromValue;

    /** NULL = open-ended top tier. */
    @Column(name = "to_value", precision = 18, scale = 4)
    private BigDecimal toValue;

    @Column(name = "saicomex_percent", precision = 9, scale = 6)
    private BigDecimal saicomexPercent;

    @Column(name = "partner_percent", precision = 9, scale = 6)
    private BigDecimal partnerPercent;

    @Column(name = "fixed_amount", precision = 18, scale = 4)
    private BigDecimal fixedAmount;

    @Column(name = "rate_amount", precision = 18, scale = 6)
    private BigDecimal rateAmount;
}
