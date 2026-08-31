package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SRS §10 "Approval history", §39 — generic approval trail shared by
 * contracts, agreements and (V6) expenses, payments, production and
 * settlements.
 */
@Entity
@Table(name = "approvals")
@Getter
@Setter
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CONTRACT | AGREEMENT | EXPENSE | PAYMENT | PRODUCTION | SETTLEMENT */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo = 1;

    @Column(name = "step_name", length = 80)
    private String stepName;

    @Column(name = "required_role", length = 40)
    private String requiredRole;

    /** SUBMITTED | APPROVED | REJECTED | RETURNED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "actor_email", nullable = false, length = 160)
    private String actorEmail;

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt = LocalDateTime.now();
}
