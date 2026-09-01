package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §19 — a supplier of fuel, explosives, spares or services. Referenced by
 * purchase orders and stock receipts. Full audit + soft delete.
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
public class Supplier extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    // FUEL | EXPLOSIVES | SPARES | SERVICES | GENERAL
    @Column(name = "supplier_type", length = 60)
    private String supplierType;

    @Column(name = "contact_person", length = 160)
    private String contactPerson;

    @Column(length = 40)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "tax_number", length = 60)
    private String taxNumber;

    @Column(name = "payment_terms", length = 80)
    private String paymentTerms;

    @Column(name = "default_currency", length = 3, columnDefinition = "char")
    private String defaultCurrency;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;
}
