package com.saicomex.entity;

import com.saicomex.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §3 — the group company at the top of the hierarchy.
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "trading_name", length = 200)
    private String tradingName;

    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    @Column(name = "tax_number", length = 60)
    private String taxNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 80)
    private String country;

    @Column(length = 40)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(length = 200)
    private String website;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "reporting_currency", nullable = false, length = 3)
    private String reportingCurrency = "USD";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
