package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.ShaftDtos.ShaftDetail;
import com.saicomex.dto.ShaftDtos.ShaftRequest;
import com.saicomex.dto.ShaftDtos.ShaftSummary;
import com.saicomex.dto.ShaftDtos.StatusUpdateRequest;
import com.saicomex.entity.Contract;
import com.saicomex.entity.MiningOperation;
import com.saicomex.entity.Partner;
import com.saicomex.entity.Project;
import com.saicomex.entity.Shaft;
import com.saicomex.entity.User;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.ContractRepository;
import com.saicomex.repository.DocumentRepository;
import com.saicomex.repository.MiningOperationRepository;
import com.saicomex.repository.PartnerRepository;
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
 * SRS §8 — shafts, the primary operational entity.
 */
@Service
@RequiredArgsConstructor
public class ShaftService {

    private static final Set<String> STATUSES = Set.of(
            "PROPOSED", "CONTRACT_PENDING", "CONTRACTED", "MOBILISATION", "DEVELOPMENT",
            "ACTIVE", "TEMPORARILY_STOPPED", "SUSPENDED", "CLOSED");

    private final ShaftRepository shaftRepository;
    private final ProjectRepository projectRepository;
    private final MiningOperationRepository operationRepository;
    private final PartnerRepository partnerRepository;
    private final ContractRepository contractRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<ShaftSummary> list(String status, Long projectId, Long operationId, Long partnerId,
                                           String search, Pageable pageable) {
        permissions.require("shafts.view");
        User me = permissions.currentUser();
        List<Long> scoped = permissions.visibleShaftIds(me);
        boolean unrestricted = scoped.isEmpty();

        Page<Shaft> page = shaftRepository.search(
                blankToNull(status), projectId, operationId, partnerId, blankToNull(search),
                unrestricted, unrestricted ? List.of(-1L) : scoped, pageable);

        return PageResponse.of(page, this::toSummary);
    }

    /** Unpaged list for dropdowns, optionally scoped to a project and/or operation. */
    @Transactional(readOnly = true)
    public List<ShaftSummary> options(Long projectId, Long operationId) {
        permissions.require("shafts.view");
        User me = permissions.currentUser();
        List<Long> scoped = permissions.visibleShaftIds(me);
        boolean unrestricted = scoped.isEmpty();

        Page<Shaft> page = shaftRepository.search(
                null, projectId, operationId, null, null,
                unrestricted, unrestricted ? List.of(-1L) : scoped, Pageable.unpaged());

        return page.getContent().stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public ShaftDetail get(Long id) {
        permissions.require("shafts.view");
        Shaft shaft = load(id);
        permissions.requireShaftAccess(shaft.getId(), shaft.getProjectId(), AuditContext.currentRole());
        return toDetail(shaft);
    }

    @Transactional
    public ShaftDetail create(ShaftRequest req) {
        permissions.require("shafts.create");
        Project project = loadProject(req.projectId());
        permissions.requireProjectAccess(project.getId(), AuditContext.currentRole());
        validate(req, null);
        checkOperationBelongsToProject(req.miningOperationId(), project.getId());
        checkOwnerPartner(req.ownerPartnerId());

        Shaft shaft = new Shaft();
        apply(shaft, req);
        shaft.setStatus(req.status() == null ? "PROPOSED" : req.status());
        Shaft saved = shaftRepository.save(shaft);

        audit.recordForShaft("CREATE", "SHAFT", saved.getId(), saved.getCode(),
                saved.getProjectId(), saved.getId(),
                "Shaft " + saved.getName() + " created with status " + saved.getStatus());
        return toDetail(saved);
    }

    @Transactional
    public ShaftDetail update(Long id, ShaftRequest req) {
        permissions.require("shafts.edit");
        Shaft shaft = load(id);
        permissions.requireShaftAccess(shaft.getId(), shaft.getProjectId(), AuditContext.currentRole());
        validate(req, shaft);

        Project project = loadProject(req.projectId());
        permissions.requireProjectAccess(project.getId(), AuditContext.currentRole());
        checkOperationBelongsToProject(req.miningOperationId(), project.getId());
        checkOwnerPartner(req.ownerPartnerId());

        audit.recordChange("SHAFT", id, shaft.getCode(), "name", shaft.getName(), req.name(), null);
        audit.recordChange("SHAFT", id, shaft.getCode(), "status", shaft.getStatus(), req.status(), null);
        audit.recordChange("SHAFT", id, shaft.getCode(), "ownerPartnerId",
                shaft.getOwnerPartnerId(), req.ownerPartnerId(), null);

        apply(shaft, req);
        if (req.status() != null) shaft.setStatus(req.status());
        return toDetail(shaftRepository.save(shaft));
    }

    /** Dedicated status transition endpoint, so a status change always carries its own reason. */
    @Transactional
    public ShaftDetail updateStatus(Long id, StatusUpdateRequest req) {
        permissions.require("shafts.edit");
        Shaft shaft = load(id);
        permissions.requireShaftAccess(shaft.getId(), shaft.getProjectId(), AuditContext.currentRole());
        if (!STATUSES.contains(req.status())) {
            throw new BusinessRuleException("Unknown shaft status: " + req.status());
        }

        String oldStatus = shaft.getStatus();
        shaft.setStatus(req.status());
        Shaft saved = shaftRepository.save(shaft);
        audit.recordChange("SHAFT", id, shaft.getCode(), "status", oldStatus, req.status(), req.reason());
        return toDetail(saved);
    }

    /**
     * SRS §39 — soft delete only. A shaft that has any contract on record is
     * refused: settlement and financial history hangs off the contract, and
     * shafts with contractual history are closed, not deleted.
     */
    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("shafts.delete");
        Shaft shaft = load(id);
        permissions.requireShaftAccess(shaft.getId(), shaft.getProjectId(), AuditContext.currentRole());

        List<Contract> contracts = contractRepository.findAllByShaftIdAndDeletedAtIsNullOrderByEffectiveDateDesc(id);
        if (!contracts.isEmpty()) {
            throw new BusinessRuleException(
                    "This shaft has " + contracts.size() + " contract(s) on record. Shafts with contractual "
                    + "history are closed, not deleted — set the status to CLOSED instead.");
        }

        shaft.softDelete(AuditContext.currentUser());
        shaftRepository.save(shaft);
        audit.recordForShaft("DELETE", "SHAFT", id, shaft.getCode(), shaft.getProjectId(), id,
                "Shaft archived" + (reason == null ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- helpers

    private Shaft load(Long id) {
        return shaftRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Shaft", id));
    }

    private Project loadProject(Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> NotFoundException.of("Project", projectId));
    }

    private void validate(ShaftRequest req, Shaft existing) {
        if (req.status() != null && !STATUSES.contains(req.status())) {
            throw new BusinessRuleException("Unknown shaft status: " + req.status());
        }
        boolean codeChanged = existing == null || !existing.getCode().equalsIgnoreCase(req.code());
        if (codeChanged && shaftRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(req.code())) {
            throw new BusinessRuleException("Shaft code " + req.code() + " is already in use");
        }
        if (req.closureDate() != null && req.startDate() != null && req.closureDate().isBefore(req.startDate())) {
            throw new BusinessRuleException("Closure date cannot be before the start date");
        }
    }

    /**
     * Mirrors {@code trg_shaft_operation_project} so the operator gets a
     * sentence instead of a database constraint name.
     */
    private void checkOperationBelongsToProject(Long operationId, Long projectId) {
        if (operationId == null) return;
        MiningOperation operation = operationRepository.findByIdAndDeletedAtIsNull(operationId)
                .orElseThrow(() -> NotFoundException.of("Mining operation", operationId));
        if (!operation.getProjectId().equals(projectId)) {
            throw new BusinessRuleException(
                    "Mining operation " + operation.getName() + " does not belong to the selected project.");
        }
    }

    private void checkOwnerPartner(Long partnerId) {
        if (partnerId == null) return;
        partnerRepository.findByIdAndDeletedAtIsNull(partnerId)
                .orElseThrow(() -> NotFoundException.of("Partner", partnerId));
    }

    private void apply(Shaft s, ShaftRequest r) {
        s.setProjectId(r.projectId());
        s.setMiningOperationId(r.miningOperationId());
        s.setCode(r.code());
        s.setName(r.name());
        s.setShaftNumber(r.shaftNumber());
        s.setDescription(r.description());
        s.setLocationId(r.locationId());
        s.setLatitude(r.latitude());
        s.setLongitude(r.longitude());
        s.setOwnerPartnerId(r.ownerPartnerId());
        s.setShaftManagerId(r.shaftManagerId());
        s.setDepthMetres(r.depthMetres());
        s.setCommissionedDate(r.commissionedDate());
        s.setStartDate(r.startDate());
        s.setClosureDate(r.closureDate());
        s.setProductionTarget(r.productionTarget());
        s.setProductionTargetUnit(r.productionTargetUnit());
        s.setProductionTargetPeriod(r.productionTargetPeriod());
        s.setNotes(r.notes());
    }

    private ShaftSummary toSummary(Shaft s) {
        String contractStatus = contractRepository
                .findAllByShaftIdAndDeletedAtIsNullOrderByEffectiveDateDesc(s.getId())
                .stream().findFirst().map(Contract::getStatus).orElse(null);
        return new ShaftSummary(
                s.getId(), s.getCode(), s.getName(), s.getShaftNumber(),
                s.getProjectId(), projectName(s.getProjectId()),
                s.getMiningOperationId(), operationName(s.getMiningOperationId()),
                s.getOwnerPartnerId(), partnerName(s.getOwnerPartnerId()),
                s.getStatus(), contractStatus,
                s.getProductionTarget(), s.getProductionTargetUnit(), s.getStartDate());
    }

    private ShaftDetail toDetail(Shaft s) {
        var activeContract = contractRepository.findByShaftIdAndStatusAndDeletedAtIsNull(s.getId(), "ACTIVE");
        return com.saicomex.dto.ShaftDtos.toDetail(s,
                projectName(s.getProjectId()), operationName(s.getMiningOperationId()),
                partnerName(s.getOwnerPartnerId()), userName(s.getShaftManagerId()),
                activeContract.map(Contract::getId).orElse(null),
                activeContract.map(Contract::getContractNumber).orElse(null),
                (int) documentRepository.countByEntityTypeAndEntityIdAndDeletedAtIsNull("SHAFT", s.getId()));
    }

    private String projectName(Long projectId) {
        if (projectId == null) return null;
        return projectRepository.findByIdAndDeletedAtIsNull(projectId).map(Project::getName).orElse(null);
    }

    private String operationName(Long operationId) {
        if (operationId == null) return null;
        return operationRepository.findByIdAndDeletedAtIsNull(operationId).map(MiningOperation::getName).orElse(null);
    }

    private String partnerName(Long partnerId) {
        if (partnerId == null) return null;
        return partnerRepository.findByIdAndDeletedAtIsNull(partnerId).map(Partner::getLegalName).orElse(null);
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
