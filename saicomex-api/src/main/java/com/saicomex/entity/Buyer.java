package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Gold buyer / refinery / offtake counterparty a {@link Sale} is made to.
 */
@Entity
@Table(name = "buyers")
@Getter
@Setter
public class Buyer extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** REFINERY | TRADER | EXPORT | LOCAL */
    @Column(name = "buyer_type", length = 40)
    private String buyerType;

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

    @Column(name = "default_currency", length = 3)
    private String defaultCurrency;

    @Column(name = "licence_number", length = 80)
    private String licenceNumber;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;
}
