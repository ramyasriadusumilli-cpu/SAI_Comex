package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.ProjectDtos.ProjectDetail;
import com.saicomex.dto.ProjectDtos.ProjectRequest;
import com.saicomex.dto.ProjectDtos.ProjectSummary;
import com.saicomex.entity.Company;
import com.saicomex.entity.Project;
import com.saicomex.entity.User;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * SRS §6 — project management.
 *
 * <p>Reference implementation for every CRUD service in this application. The
 * shape is: check permission, check data scope, validate, mutate, audit.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    /** SRS §6. Kept here rather than as a database enum so a new status is a
     *  one-line change plus a migration, not a type rewrite. */
    private static final Set<String> STATUSES = Set.of(
            "PROPOSED", "PLANNING", "PROSPECTING", "DEVELOPMENT",
            "ACTIVE", "SUSPENDED", "COMPLETED", "CLOSED");

    private final ProjectRepository projectRepository;
    private final MiningOperationRepository operationRepository;
    private final ShaftRepository shaftRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<ProjectSummary> list(String status, String type, String search, Pageable pageable) {
        permissions.require("projects.view");
        User me = permissions.currentUser();
        List<Long> scoped = permissions.visibleProjectIds(me);
        boolean unrestricted = scoped.isEmpty();

        Page<Project> page = projectRepository.search(
                blankToNull(status), blankToNull(type), blankToNull(search),
                unrestricted, unrestricted ? List.of(-1L) : scoped, pageable);

        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummary> listAll() {
        permissions.require("projects.view");
        User me = permissions.currentUser();
        List<Long> scoped = permissions.visibleProjectIds(me);
        return projectRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .filter(p -> scoped.isEmpty() || scoped.contains(p.getId()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetail get(Long id) {
        permissions.require("projects.view");
        Project project = load(id);
        permissions.requireProjectAccess(project.getId(), AuditContext.currentRole());
        return toDetail(project);
    }

    @Transactional
    public ProjectDetail create(ProjectRequest req) {
        permissions.require("projects.create");
        validate(req, null);

        Project project = new Project();
        project.setCompanyId(defaultCompanyId());
        apply(project, req);
        project.setStatus(req.status() == null ? "PROPOSED" : req.status());
        Project saved = projectRepository.save(project);

        audit.record("CREATE", "PROJECT", saved.getId(), saved.getCode(),
                "Project " + saved.getName() + " created with status " + saved.getStatus());
        return toDetail(saved);
    }

    @Transactional
    public ProjectDetail update(Long id, ProjectRequest req) {
        permissions.require("projects.edit");
        Project project = load(id);
        permissions.requireProjectAccess(project.getId(), AuditContext.currentRole());
        validate(req, id);

        // Field-level audit before mutation, so old and new values are both real.
        audit.recordChange("PROJECT", id, project.getCode(), "name", project.getName(), req.name(), null);
        audit.recordChange("PROJECT", id, project.getCode(), "status", project.getStatus(), req.status(), null);
        audit.recordChange("PROJECT", id, project.getCode(), "projectManagerId",
                project.getProjectManagerId(), req.projectManagerId(), null);

        apply(project, req);
        if (req.status() != null) project.setStatus(req.status());
        return toDetail(projectRepository.save(project));
    }

    /**
     * SRS §39 — soft delete only. A project with operations or shafts under it
     * is refused outright: cascading a delete through a hierarchy that carries
     * production and settlement history is not a thing this application does.
     */
    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("projects.delete");
        Project project = load(id);

        long operations = operationRepository.findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(id).size();
        long shafts = shaftRepository.findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(id).size();
        if (operations > 0 || shafts > 0) {
            throw new BusinessRuleException(
                    "This project still has " + operations + " operation(s) and " + shafts
                    + " shaft(s). Close or move them first — projects are never deleted with history attached.");
        }

        project.softDelete(AuditContext.currentUser());
        projectRepository.save(project);
        audit.record("DELETE", "PROJECT", id, project.getCode(),
                "Project archived" + (reason == null ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- helpers

    private Project load(Long id) {
        return projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Project", id));
    }

    private void validate(ProjectRequest req, Long existingId) {
        if (req.status() != null && !STATUSES.contains(req.status())) {
            throw new BusinessRuleException("Unknown project status: " + req.status());
        }
        projectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(req.code())
                .filter(p -> !p.getId().equals(existingId))
                .ifPresent(p -> {
                    throw new BusinessRuleException("Project code " + req.code() + " is already in use by " + p.getName());
                });
        if (req.plannedCompletionDate() != null && req.startDate() != null
                && req.plannedCompletionDate().isBefore(req.startDate())) {
            throw new BusinessRuleException("Planned completion cannot be before the start date");
        }
    }

    private void apply(Project p, ProjectRequest r) {
        p.setCode(r.code());
        p.setName(r.name());
        p.setProjectType(r.projectType());
        p.setDescription(r.description());
        p.setLocationName(r.locationName());
        p.setLatitude(r.latitude());
        p.setLongitude(r.longitude());
        p.setBoundaryGeojson(r.boundaryGeojson());
        p.setProjectManagerId(r.projectManagerId());
        p.setStartDate(r.startDate());
        p.setPlannedCompletionDate(r.plannedCompletionDate());
        p.setActualCompletionDate(r.actualCompletionDate());
        p.setBudgetAmount(r.budgetAmount());
        p.setBudgetCurrency(r.budgetCurrency());
        p.setLicenceNumber(r.licenceNumber());
        p.setLicenceExpiryDate(r.licenceExpiryDate());
        p.setPermitNumber(r.permitNumber());
        p.setPermitExpiryDate(r.permitExpiryDate());
        p.setNotes(r.notes());
    }

    private ProjectSummary toSummary(Project p) {
        var shafts = shaftRepository.findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(p.getId());
        int active = (int) shafts.stream().filter(s -> "ACTIVE".equals(s.getStatus())).count();
        return new ProjectSummary(
                p.getId(), p.getCode(), p.getName(), p.getProjectType(), p.getStatus(),
                p.getLocationName(), userName(p.getProjectManagerId()), p.getStartDate(),
                operationRepository.findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(p.getId()).size(),
                shafts.size(), active,
                p.getBudgetAmount(), p.getBudgetCurrency());
    }

    private ProjectDetail toDetail(Project p) {
        return com.saicomex.dto.ProjectDtos.toDetail(p,
                userName(p.getProjectManagerId()),
                operationRepository.findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(p.getId()).size(),
                shaftRepository.findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(p.getId()).size(),
                (int) documentRepository.countByEntityTypeAndEntityIdAndDeletedAtIsNull("PROJECT", p.getId()));
    }

    private String userName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse(null);
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream()
                .findFirst()
                .map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
