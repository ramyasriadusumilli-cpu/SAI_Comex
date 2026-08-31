package com.saicomex.dto;

import com.saicomex.entity.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §6. Request and response shapes for projects.
 *
 * <p>Grouped in one file per aggregate: a project has three DTOs that change
 * together, and thirty single-record files make that harder to see, not easier.
 */
public final class ProjectDtos {

    private ProjectDtos() {}

    /** Create / update payload. */
    public record ProjectRequest(
            @NotBlank @Size(max = 30)  String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 60)  String projectType,
            String description,
            @Size(max = 200) String locationName,
            BigDecimal latitude,
            BigDecimal longitude,
            String boundaryGeojson,
            Long projectManagerId,
            LocalDate startDate,
            LocalDate plannedCompletionDate,
            LocalDate actualCompletionDate,
            String status,
            BigDecimal budgetAmount,
            @Size(max = 3) String budgetCurrency,
            @Size(max = 80) String licenceNumber,
            LocalDate licenceExpiryDate,
            @Size(max = 80) String permitNumber,
            LocalDate permitExpiryDate,
            String notes
    ) {}

    /** Row shape for the projects list. */
    public record ProjectSummary(
            Long id,
            String code,
            String name,
            String projectType,
            String status,
            String locationName,
            String projectManagerName,
            LocalDate startDate,
            int operationCount,
            int shaftCount,
            int activeShaftCount,
            BigDecimal budgetAmount,
            String budgetCurrency
    ) {}

    /** Full record for the project detail page. */
    public record ProjectDetail(
            Long id,
            String code,
            String name,
            String projectType,
            String description,
            String locationName,
            BigDecimal latitude,
            BigDecimal longitude,
            String boundaryGeojson,
            Long projectManagerId,
            String projectManagerName,
            LocalDate startDate,
            LocalDate plannedCompletionDate,
            LocalDate actualCompletionDate,
            String status,
            BigDecimal budgetAmount,
            String budgetCurrency,
            String licenceNumber,
            LocalDate licenceExpiryDate,
            String permitNumber,
            LocalDate permitExpiryDate,
            String notes,
            int operationCount,
            int shaftCount,
            int documentCount,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime updatedAt,
            String updatedBy
    ) {}

    public static ProjectDetail toDetail(Project p, String managerName,
                                         int operationCount, int shaftCount, int documentCount) {
        return new ProjectDetail(
                p.getId(), p.getCode(), p.getName(), p.getProjectType(), p.getDescription(),
                p.getLocationName(), p.getLatitude(), p.getLongitude(), p.getBoundaryGeojson(),
                p.getProjectManagerId(), managerName,
                p.getStartDate(), p.getPlannedCompletionDate(), p.getActualCompletionDate(),
                p.getStatus(), p.getBudgetAmount(), p.getBudgetCurrency(),
                p.getLicenceNumber(), p.getLicenceExpiryDate(),
                p.getPermitNumber(), p.getPermitExpiryDate(), p.getNotes(),
                operationCount, shaftCount, documentCount,
                p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy());
    }
}
