package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §37 — a single grantable permission, e.g. {@code shafts.edit}. Pure
 * lookup data maintained by the platform, not by tenants, so it carries no
 * audit columns.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String code;

    /** Drives nav visibility, e.g. {@code shafts}. */
    @Column(nullable = false, length = 40)
    private String module;

    /** view | create | edit | delete | approve | export */
    @Column(nullable = false, length = 40)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String description;
}
