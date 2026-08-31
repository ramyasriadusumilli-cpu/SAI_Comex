package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §19 — a physical store: a general store, a fuel bay, or a magazine
 * (the licensed store from which controlled items are issued). Reference data,
 * no audit or soft-delete columns; retired via {@code isActive}.
 */
@Entity
@Table(name = "store_locations")
@Getter
@Setter
public class StoreLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "location_id")
    private Long locationId;

    // GENERAL | FUEL_BAY | MAGAZINE
    @Column(name = "store_type", nullable = false, length = 30)
    private String storeType = "GENERAL";

    @Column(name = "keeper_user_id")
    private Long keeperUserId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
