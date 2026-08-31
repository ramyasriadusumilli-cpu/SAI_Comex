package com.saicomex.entity;

import com.saicomex.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SRS §34 — a shared location/GPS record, referenced by projects, mining
 * operations, shafts, stores and equipment so the whole portfolio can be
 * mapped in one query.
 */
@Entity
@Table(name = "locations")
@Getter
@Setter
public class Location extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    /** PROJECT | OPERATION | SHAFT | STORE | OFFICE | PLANT */
    @Column(name = "location_type", nullable = false, length = 40)
    private String locationType;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 120)
    private String region;

    @Column(length = 80)
    private String country;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * GeoJSON polygon of the site boundary, kept as text rather than PostGIS
     * so the platform runs on stock postgres:16-alpine.
     */
    @Column(name = "boundary_geojson", columnDefinition = "TEXT")
    private String boundaryGeojson;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
