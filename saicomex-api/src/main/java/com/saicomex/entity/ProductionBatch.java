package com.saicomex.entity;

import com.saicomex.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SRS §13 — groups production into a saleable lot (smelt / pour / parcel);
 * {@link Sale#getBatchId()} links a sale back to the batch it came from.
 */
@Entity
@Table(name = "production_batches")
@Getter
@Setter
public class ProductionBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "shaft_id")
    private Long shaftId;

    @Column(name = "batch_number", nullable = false, length = 50, unique = true)
    private String batchNumber;

    @Column(name = "batch_date", nullable = false)
    private LocalDate batchDate;

    @Column(name = "total_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalQuantity = BigDecimal.ZERO;

    @Column(name = "unit_code", nullable = false, length = 20)
    private String unitCode;

    @Column(precision = 12, scale = 6)
    private BigDecimal grade;

    @Column(name = "assay_reference", length = 80)
    private String assayReference;

    /** OPEN | CLOSED | SOLD */
    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(columnDefinition = "TEXT")
    private String notes;
}
