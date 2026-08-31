package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * SRS §9 — a partner / shaft owner. Central database: one partner may own or
 * participate in many shafts across many projects.
 *
 * <p>Banking fields are restricted data (only roles holding {@code
 * partners.banking} see them); the DTO layer, not this entity, is
 * responsible for stripping them for everyone else.
 */
@Entity
@Table(name = "partners")
@Getter
@Setter
public class Partner extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 30, unique = true)
    private String code;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trading_name", length = 200)
    private String tradingName;

    /** COMPANY | INDIVIDUAL | COOPERATIVE | JV */
    @Column(name = "partner_type", nullable = false, length = 30)
    private String partnerType = "COMPANY";

    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    @Column(name = "tax_number", length = 60)
    private String taxNumber;

    @Column(name = "id_number", length = 60)
    private String idNumber;

    @Column(name = "contact_person", length = 160)
    private String contactPerson;

    @Column(length = 40)
    private String phone;

    @Column(name = "alternate_phone", length = 40)
    private String alternatePhone;

    @Column(length = 160)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 120)
    private String city;

    @Column(length = 80)
    private String country;

    @Column(name = "bank_name", length = 160)
    private String bankName;

    @Column(name = "bank_branch", length = 120)
    private String bankBranch;

    @Column(name = "bank_account_name", length = 160)
    private String bankAccountName;

    @Column(name = "bank_account_number", length = 60)
    private String bankAccountNumber;

    @Column(name = "bank_swift", length = 30)
    private String bankSwift;

    @Column(name = "payment_currency", length = 3)
    private String paymentCurrency;

    /** EFT | CASH | MOBILE | CHEQUE */
    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    /** ACTIVE | INACTIVE | BLACKLISTED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "onboarded_date")
    private LocalDate onboardedDate;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
