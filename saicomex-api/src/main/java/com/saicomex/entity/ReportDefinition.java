package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * SRS §28 — a saved report definition, so the report list is data-driven.
 */
@Entity
@Table(name = "report_definitions")
@Getter
@Setter
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60, unique = true)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** GROUP | PROJECT | SHAFT | OPERATIONAL | FINANCIAL */
    @Column(name = "report_group", nullable = false, length = 30)
    private String reportGroup;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "required_permission", length = 80)
    private String requiredPermission;

    @Column(name = "supports_pdf", nullable = false)
    private Boolean supportsPdf = true;

    @Column(name = "supports_excel", nullable = false)
    private Boolean supportsExcel = true;

    @Column(name = "supports_csv", nullable = false)
    private Boolean supportsCsv = true;

    @Column(name = "default_period", nullable = false, length = 20)
    private String defaultPeriod = "MONTH";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 100;
}
