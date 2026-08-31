package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.MiningOperationDtos.OperationDetail;
import com.saicomex.dto.MiningOperationDtos.OperationRequest;
import com.saicomex.dto.MiningOperationDtos.OperationSummary;
import com.saicomex.dto.PageResponse;
import com.saicomex.entity.MiningOperation;
import com.saicomex.entity.Project;
import com.saicomex.entity.User;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.DocumentRepository;
import com.saicomex.repository.MiningOperationRepository;
import com.saicomex.repository.ProjectRepository;
import com.saicomex.repository.ShaftRepository;
import com.saicomex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * SRS §7 — mining operations, one level below a project and above shafts.
 */
@Service
@RequiredArgsConstructor
public class MiningOperationService {

    private static final Set<String> STATUSES = Set.of(
            "PROPOSED", "DEVELOPMENT", "ACTIVE", "SUSPENDED", "CLOSED");

    private final MiningOperationRepository operationRepository;
    private final ProjectRepository projectRepository;
    private final ShaftRepository shaftRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<OperationSummary> list(String status, Long projectId, String search, Pageable pageable) {
        permissions.require("operations.view");
        User me = permissions.currentUser();
        List<Long> scoped = permissions.visibleProjectIds(me);
        boolean unrestricted = scoped.isEmpty();

        Page<MiningOperation> page = operationRepository.search(
                blankToNull(status), projectId, blankToNull(search),
                unrestricted, unrestricted ? List.of(-1L) : scoped, pageable);

        return PageResponse.of(page, this::toSummary);
    }

    /** Unpaged list for dropdowns, optionally scoped to one project. */
    @Transactional(readOnly = true)
    public List<OperationSummary> options(Long projectId) {
        permissions.require("operations.view");
        User me = permissions.currentUser();
        List<Long> scoped = permissions.visibleProjectIds(me);
        boolean unrestricted = scoped.isEmpty();

        Page<MiningOperation> page = operationRepository.search(
                null, projectId, null, unrestricted, unrestricted ? List.of(-1L) : scoped, Pageable.unpaged());

        return page.getContent().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public OperationDetail get(Long id) {
        permissions.require("operations.view");
        MiningOperation operation = load(id);
        permissions.requireProjectAccess(operation.getProjectId(), AuditContext.currentRole());
        return toDetail(operation);
    }

    @Transactional
    public OperationDetail create(OperationRequest req) {
        permissions.require("operations.create");
        Project project = loadProject(req.projectId());
        permissions.requireProjectAccess(project.getId(), AuditContext.currentRole());
        validate(req, null);

        MiningOperation operation = new MiningOperation();
        apply(operation, req);
        operation.setStatus(req.status() == null ? "PROPOSED" : req.status());
        MiningOperation saved = operationRepository.save(operation);

        audit.record("CREATE", "OPERATION", saved.getId(), saved.getCode(),
                "Operation " + saved.getName() + " created under project " + project.getName());
        return toDetail(saved);
    }

    @Transactional
    public OperationDetail update(Long id, OperationRequest req) {
        permissions.require("operations.edit");
        MiningOperation operation = load(id);
        permissions.requireProjectAccess(operation.getProjectId(), AuditContext.currentRole());
        validate(req, operation);

        Project project = loadProject(req.projectId());
        permissions.requireProjectAccess(project.getId(), AuditContext.currentRole());

        audit.recordChange("OPERATION", id, operation.getCode(), "name", operation.getName(), req.name(), null);
        audit.recordChange("OPERATION", id, operation.getCode(), "status", operation.getStatus(), req.status(), null);

        apply(operation, req);
        if (req.status() != null) operation.setStatus(req.status());
        return toDetail(operationRepository.save(operation));
    }

    /**
     * SRS §39 — soft delete only. An operation that still has shafts under it
     * is refused: those shafts would be left pointing at a deleted parent.
     */
    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("operations.delete");
        MiningOperation operation = load(id);
        permissions.requireProjectAccess(operation.getProjectId(), AuditContext.currentRole());

        long shafts = shaftRepository.findAllByMiningOperationIdAndDeletedAtIsNullOrderByNameAsc(id).size();
        if (shafts > 0) {
            throw new BusinessRuleException(
                    "This operation still has " + shafts + " shaft(s) assigned to it. Reassign or close them first.");
        }

        operation.softDelete(AuditContext.currentUser());
        operationRepository.save(operation);
        audit.record("DELETE", "OPERATION", id, operation.getCode(),
                "Operation archived" + (reason == null ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- helpers

    private MiningOperation load(Long id) {
        return operationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Mining operation", id));
    }

    private Project loadProject(Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> NotFoundException.of("Project", projectId));
    }

    private void validate(OperationRequest req, MiningOperation existing) {
        if (req.status() != null && !STATUSES.contains(req.status())) {
            throw new BusinessRuleException("Unknown operation status: " + req.status());
        }
        boolean codeChanged = existing == null || !existing.getCode().equalsIgnoreCase(req.code());
        if (codeChanged && operationRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(req.code())) {
            throw new BusinessRuleException("Operation code " + req.code() + " is already in use");
        }
        if (req.endDate() != null && req.startDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessRuleException("End date cannot be before the start date");
        }
    }

    private void apply(MiningOperation o, OperationRequest r) {
        o.setProjectId(r.projectId());
        o.setCode(r.code());
        o.setName(r.name());
        o.setOperationType(r.operationType());
        o.setDescription(r.description());
        o.setLocationId(r.locationId());
        o.setLatitude(r.latitude());
        o.setLongitude(r.longitude());
        o.setManagerId(r.managerId());
        o.setStartDate(r.startDate());
        o.setEndDate(r.endDate());
        o.setBudgetAmount(r.budgetAmount());
        o.setBudgetCurrency(r.budgetCurrency());
        o.setNotes(r.notes());
    }

    private OperationSummary toSummary(MiningOperation o) {
        var shafts = shaftRepository.findAllByMiningOperationIdAndDeletedAtIsNullOrderByNameAsc(o.getId());
        int active = (int) shafts.stream().filter(s -> "ACTIVE".equals(s.getStatus())).count();
        return new OperationSummary(
                o.getId(), o.getCode(), o.getName(), o.getOperationType(), o.getStatus(),
                o.getProjectId(), projectName(o.getProjectId()), userName(o.getManagerId()),
                o.getStartDate(), shafts.size(), active,
                o.getBudgetAmount(), o.getBudgetCurrency());
    }

    private OperationDetail toDetail(MiningOperation o) {
        return com.saicomex.dto.MiningOperationDtos.toDetail(o,
                projectName(o.getProjectId()), userName(o.getManagerId()),
                shaftRepository.findAllByMiningOperationIdAndDeletedAtIsNullOrderByNameAsc(o.getId()).size(),
                (int) documentRepository.countByEntityTypeAndEntityIdAndDeletedAtIsNull("OPERATION", o.getId()));
    }

    private String projectName(Long projectId) {
        if (projectId == null) return null;
        return projectRepository.findByIdAndDeletedAtIsNull(projectId).map(Project::getName).orElse(null);
    }

    private String userName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse(null);
    }

    static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
