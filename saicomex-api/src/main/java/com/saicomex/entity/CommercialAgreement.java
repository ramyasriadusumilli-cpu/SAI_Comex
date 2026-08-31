package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §11 — the commercial agreement header: what basis the split is
 * computed on and what defaults apply when no specific {@link AgreementRule}
 * matches.
 */
@Entity
@Table(name = "commercial_agreements")
@Getter
@Setter
public class CommercialAgreement extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "contract_version_id")
    private Long contractVersionId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** DRAFT | PENDING_APPROVAL | ACTIVE | SUPERSEDED | TERMINATED */
    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    /** NET_REVENUE | GROSS_REVENUE | PRODUCTION | PROFIT */
    @Column(name = "settlement_basis", nullable = false, length = 30)
    private String settlementBasis = "NET_REVENUE";

    @Column(name = "default_saicomex_percent", precision = 9, scale = 6)
    private BigDecimal defaultSaicomexPercent;

    @Column(name = "default_partner_percent", precision = 9, scale = 6)
    private BigDecimal defaultPartnerPercent;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "rounding_scale", nullable = false)
    private Short roundingScale = 2;

    @Column(name = "rounding_mode", nullable = false, length = 20)
    private String roundingMode = "HALF_UP";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
}
