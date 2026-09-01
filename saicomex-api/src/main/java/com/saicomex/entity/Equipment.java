package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §20-22 — an item of plant or a vehicle in the asset register. The
 * placement columns (project/operation/shaft) hold the CURRENT placement only;
 * the history lives in {@link EquipmentAllocation} and is written whenever the
 * equipment is re-allocated. Full audit + soft delete.
 */
@Entity
@Table(name = "equipment")
@Getter
@Setter
public class Equipment extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "asset_number", nullable = false, length = 40, unique = true)
    private String assetNumber;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "equipment_type", nullable = false, length = 60)
    private String equipmentType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 120)
    private String manufacturer;

    @Column(length = 120)
    private String model;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Column(name = "registration_number", length = 60)
    private String registrationNumber;

    @Column(name = "year_of_manufacture")
    private Integer yearOfManufacture;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost", precision = 18, scale = 4)
    private BigDecimal purchaseCost;

    @Column(name = "purchase_currency", length = 3, columnDefinition = "char")
    private String purchaseCurrency;

    @Column(name = "current_value", precision = 18, scale = 4)
    private BigDecimal currentValue;

    // OWNED | LEASED | PARTNER | HIRED
    @Column(nullable = false, length = 20)
    private String ownership = "OWNED";

    @Column(name = "owner_partner_id")
    private Long ownerPartnerId;

    @Column(name = "supplier_id")
    private Long supplierId;

    // Current placement (history in equipment_allocations).
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "mining_operation_id")
    private Long miningOperationId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "operator_employee_id")
    private Long operatorEmployeeId;

    @Column(name = "operating_hours", nullable = false, precision = 12, scale = 2)
    private BigDecimal operatingHours = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal odometer;

    @Column(name = "service_interval_hours", precision = 12, scale = 2)
    private BigDecimal serviceIntervalHours;

    @Column(name = "next_service_hours", precision = 12, scale = 2)
    private BigDecimal nextServiceHours;

    @Column(name = "next_service_date")
    private LocalDate nextServiceDate;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    // ACTIVE | STANDBY | UNDER_MAINTENANCE | BREAKDOWN | DECOMMISSIONED | DISPOSED
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;
}
