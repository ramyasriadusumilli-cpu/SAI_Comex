package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.ContractDtos.AgreementRef;
import com.saicomex.dto.ContractDtos.AmendmentRequest;
import com.saicomex.dto.ContractDtos.ApprovalEntry;
import com.saicomex.dto.ContractDtos.ContractDetail;
import com.saicomex.dto.ContractDtos.ContractRequest;
import com.saicomex.dto.ContractDtos.ContractSummary;
import com.saicomex.dto.ContractDtos.ContractVersionDto;
import com.saicomex.dto.PageResponse;
import com.saicomex.entity.Contract;
import com.saicomex.entity.ContractType;
import com.saicomex.entity.ContractVersion;
import com.saicomex.entity.Partner;
import com.saicomex.entity.Project;
import com.saicomex.entity.Shaft;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.CommercialAgreementRepository;
import com.saicomex.repository.ContractRepository;
import com.saicomex.repository.ContractTypeRepository;
import com.saicomex.repository.ContractVersionRepository;
import com.saicomex.repository.ApprovalRepository;
import com.saicomex.repository.DocumentRepository;
import com.saicomex.repository.PartnerRepository;
import com.saicomex.repository.ProjectRepository;
import com.saicomex.repository.ShaftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * SRS §10 — contract lifecycle and versioning.
 *
 * <p>{@code activate} enforces two conditions the database constraints alone
 * cannot make legible to an operator: the one-active-contract-per-shaft rule
 * (backed by {@code uq_contract_active_per_shaft}, but restated here as a
 * message that says what to do next) and the requirement that an ACTIVE
 * contract always has an ACTIVE commercial agreement behind it.
 */
@Service
@RequiredArgsConstructor
public class ContractService {

    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "PENDING_APPROVAL", "APPROVED", "ACTIVE", "EXPIRED", "TERMINATED", "SUPERSEDED");

    private final ContractRepository contractRepository;
    private final ContractVersionRepository contractVersionRepository;
    private final CommercialAgreementRepository agreementRepository;
    private final ApprovalRepository approvalRepository;
    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final ShaftRepository shaftRepository;
    private final PartnerRepository partnerRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<ContractSummary> list(String status, Long projectId, Long shaftId, Long partnerId,
                                              Long contractTypeId, String search, Pageable pageable) {
        permissions.require("contracts.view");
        Page<Contract> page = contractRepository.search(
                blankToNull(status), projectId, shaftId, partnerId, contractTypeId, blankToNull(search), pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public ContractDetail get(Long id) {
        permissions.require("contracts.view");
        return toDetail(load(id));
    }

    @Transactional
    public ContractDetail create(ContractRequest req) {
        permissions.require("contracts.create");
        validate(req, null);

        Contract contract = new Contract();
        contract.setCompanyId(companyIdOf(req.projectId()));
        apply(contract, req);
        contract.setStatus(req.status() == null ? "DRAFT" : req.status());
        Contract saved = contractRepository.save(contract);

        audit.record("CREATE", "CONTRACT", saved.getId(), saved.getContractNumber(),
                "Contract " + saved.getContractNumber() + " created with status " + saved.getStatus());
        return toDetail(saved);
    }

    @Transactional
    public ContractDetail update(Long id, ContractRequest req) {
        permissions.require("contracts.edit");
        Contract contract = load(id);
        validate(req, contract);

        audit.recordChange("CONTRACT", id, contract.getContractNumber(), "status", contract.getStatus(), req.status(), null);
        audit.recordChange("CONTRACT", id, contract.getContractNumber(), "expiryDate", contract.getExpiryDate(), req.expiryDate(), null);

        apply(contract, req);
        if (req.status() != null) contract.setStatus(req.status());
        return toDetail(contractRepository.save(contract));
    }

    /**
     * Moves DRAFT or APPROVED to ACTIVE. Refused if the shaft already has a
     * different ACTIVE contract (supersede or terminate it first — the unique
     * index would also reject the save, but this message is the one the
     * operator actually reads) or if the contract has no ACTIVE commercial
     * agreement, since an active contract with no commercial terms cannot
     * settle.
     */
    @Transactional
    public ContractDetail activate(Long id) {
        permissions.require("contracts.approve");
        Contract contract = load(id);

        if (!"DRAFT".equals(contract.getStatus()) && !"APPROVED".equals(contract.getStatus())) {
            throw new BusinessRuleException(
                    "Only a DRAFT or APPROVED contract can be activated (current status: " + contract.getStatus() + ")");
        }

        if (contract.getShaftId() != null) {
            contractRepository.findByShaftIdAndStatusAndDeletedAtIsNull(contract.getShaftId(), "ACTIVE")
                    .filter(other -> !other.getId().equals(contract.getId()))
                    .ifPresent(other -> {
                        throw new BusinessRuleException(
                                "Shaft already has an active contract (" + other.getContractNumber()
                                + "). Supersede or terminate it before activating this one.");
                    });
        }

        boolean hasActiveAgreement = agreementRepository
                .findByContractIdAndStatusAndDeletedAtIsNull(id, "ACTIVE").isPresent();
        if (!hasActiveAgreement) {
            throw new BusinessRuleException(
                    "This contract has no ACTIVE commercial agreement. An active contract with no commercial "
                    + "terms cannot settle — activate an agreement for it first.");
        }

        contract.setStatus("ACTIVE");
        contract.setApprovedBy(AuditContext.currentUser());
        contract.setApprovedAt(LocalDateTime.now());
        Contract saved = contractRepository.save(contract);

        audit.record("ACTIVATE", "CONTRACT", id, contract.getContractNumber(), "Contract activated");
        return toDetail(saved);
    }

    @Transactional
    public ContractDetail terminate(Long id, String reason) {
        permissions.require("contracts.approve");
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A termination reason is required");
        }
        Contract contract = load(id);

        contract.setStatus("TERMINATED");
        contract.setTerminationNotes(reason);
        Contract saved = contractRepository.save(contract);

        audit.record("TERMINATE", "CONTRACT", id, contract.getContractNumber(), "Contract terminated — " + reason);
        return toDetail(saved);
    }

    /**
     * SRS §10 contract versioning. Creates the next {@link ContractVersion}
     * row, closes the previous current version's window to the day before
     * this one starts, and bumps {@code contracts.current_version}. The
     * previous pair of rows is left intact so a historical settlement can
     * always be recomputed exactly as it was.
     */
    @Transactional
    public ContractDetail amend(Long id, AmendmentRequest req) {
        permissions.require("contracts.edit");
        Contract contract = load(id);

        Integer maxVersion = contractVersionRepository.findMaxVersionNumber(id);
        if (maxVersion != null) {
            contractVersionRepository.findByContractIdAndVersionNumber(id, maxVersion).ifPresent(prev -> {
                if (req.effectiveFrom().isBefore(prev.getEffectiveFrom())) {
                    throw new BusinessRuleException(
                            "The new version's effective date cannot be before the current version's start ("
                            + prev.getEffectiveFrom() + ")");
                }
                prev.setEffectiveTo(req.effectiveFrom().minusDays(1));
                prev.setStatus("SUPERSEDED");
                contractVersionRepository.save(prev);
            });
        }

        int nextVersion = maxVersion == null ? 1 : maxVersion + 1;
        ContractVersion version = new ContractVersion();
        version.setContractId(id);
        version.setVersionNumber(nextVersion);
        version.setEffectiveFrom(req.effectiveFrom());
        version.setChangeReason(req.changeReason());
        version.setChangeSummary(req.changeSummary());
        version.setStatus("ACTIVE");
        contractVersionRepository.save(version);

        contract.setCurrentVersion(nextVersion);
        Contract saved = contractRepository.save(contract);

        audit.record("AMEND", "CONTRACT", id, contract.getContractNumber(),
                "Contract amended to version " + nextVersion + " — " + req.changeReason());
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public List<ContractVersionDto> versions(Long id) {
        permissions.require("contracts.view");
        load(id);
        return contractVersionRepository.findAllByContractIdOrderByVersionNumberDesc(id).stream()
                .map(this::toVersionDto)
                .toList();
    }

    /** SRS §31 alert support — contracts expiring within {@code days}. */
    @Transactional(readOnly = true)
    public List<ContractSummary> expiring(int days) {
        permissions.require("contracts.view");
        LocalDate today = LocalDate.now();
        return contractRepository.findExpiringBetween(today, today.plusDays(days)).stream()
                .map(this::toSummary)
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private Contract load(Long id) {
        return contractRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Contract", id));
    }

    private void validate(ContractRequest req, Contract existing) {
        if (req.status() != null && !STATUSES.contains(req.status())) {
            throw new BusinessRuleException("Unknown contract status: " + req.status());
        }
        boolean numberChanged = existing == null || !req.contractNumber().equalsIgnoreCase(existing.getContractNumber());
        if (numberChanged && contractRepository.existsByContractNumberIgnoreCaseAndDeletedAtIsNull(req.contractNumber())) {
            throw new BusinessRuleException("Contract number " + req.contractNumber() + " is already in use");
        }
        if (req.expiryDate() != null && req.expiryDate().isBefore(req.effectiveDate())) {
            throw new BusinessRuleException("Expiry date cannot be before the effective date");
        }
        projectRepository.findByIdAndDeletedAtIsNull(req.projectId())
                .orElseThrow(() -> NotFoundException.of("Project", req.projectId()));
        shaftRepository.findByIdAndDeletedAtIsNull(req.shaftId())
                .orElseThrow(() -> NotFoundException.of("Shaft", req.shaftId()));
        partnerRepository.findByIdAndDeletedAtIsNull(req.partnerId())
                .orElseThrow(() -> NotFoundException.of("Partner", req.partnerId()));
        if (req.contractTypeId() != null && contractTypeRepository.findById(req.contractTypeId()).isEmpty()) {
            throw NotFoundException.of("ContractType", req.contractTypeId());
        }
    }

    private void apply(Contract c, ContractRequest r) {
        c.setContractNumber(r.contractNumber());
        c.setProjectId(r.projectId());
        c.setMiningOperationId(r.miningOperationId());
        c.setShaftId(r.shaftId());
        c.setPartnerId(r.partnerId());
        c.setContractTypeId(r.contractTypeId());
        c.setTitle(r.title());
        c.setEffectiveDate(r.effectiveDate());
        c.setExpiryDate(r.expiryDate());
        c.setRenewalDate(r.renewalDate());
        c.setSignedDate(r.signedDate());
        c.setSettlementCurrency(r.settlementCurrency() == null ? "USD" : r.settlementCurrency());
        c.setSettlementFrequency(r.settlementFrequency() == null ? "MONTHLY" : r.settlementFrequency());
        c.setSpecialConditions(r.specialConditions());
    }

    private Long companyIdOf(Long projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .map(Project::getCompanyId)
                .orElseThrow(() -> NotFoundException.of("Project", projectId));
    }

    private ContractSummary toSummary(Contract c) {
        boolean hasActiveAgreement = agreementRepository
                .findByContractIdAndStatusAndDeletedAtIsNull(c.getId(), "ACTIVE").isPresent();
        return new ContractSummary(
                c.getId(), c.getContractNumber(), projectName(c.getProjectId()), shaftName(c.getShaftId()),
                partnerName(c.getPartnerId()), contractTypeName(c.getContractTypeId()), c.getStatus(),
                c.getEffectiveDate(), c.getExpiryDate(), c.getSettlementCurrency(), hasActiveAgreement);
    }

    private ContractDetail toDetail(Contract c) {
        List<ContractVersionDto> versions = contractVersionRepository
                .findAllByContractIdOrderByVersionNumberDesc(c.getId()).stream()
                .map(this::toVersionDto)
                .toList();
        List<AgreementRef> agreements = agreementRepository
                .findAllByContractIdAndDeletedAtIsNullOrderByEffectiveFromDesc(c.getId()).stream()
                .map(a -> new AgreementRef(a.getId(), a.getName(), a.getStatus(), a.getEffectiveFrom(),
                        a.getEffectiveTo(), a.getSettlementBasis()))
                .toList();
        List<ApprovalEntry> approvalHistory = approvalRepository
                .findAllByEntityTypeAndEntityIdOrderByActedAtDesc("CONTRACT", c.getId()).stream()
                .map(a -> new ApprovalEntry(a.getId(), a.getStepNo(), a.getStepName(), a.getRequiredRole(),
                        a.getAction(), a.getActorEmail(), a.getActorRole(), a.getComments(), a.getActedAt()))
                .toList();
        int documentCount = (int) documentRepository.countByEntityTypeAndEntityIdAndDeletedAtIsNull("CONTRACT", c.getId());

        return com.saicomex.dto.ContractDtos.toDetail(c, projectName(c.getProjectId()), shaftName(c.getShaftId()),
                partnerName(c.getPartnerId()), contractTypeName(c.getContractTypeId()),
                versions, agreements, approvalHistory, documentCount);
    }

    private ContractVersionDto toVersionDto(ContractVersion v) {
        return new ContractVersionDto(v.getId(), v.getVersionNumber(), v.getEffectiveFrom(), v.getEffectiveTo(),
                v.getChangeReason(), v.getChangeSummary(), v.getStatus(), v.getApprovedBy(), v.getApprovedAt());
    }

    private String projectName(Long id) {
        return id == null ? null : projectRepository.findByIdAndDeletedAtIsNull(id).map(Project::getName).orElse(null);
    }

    private String shaftName(Long id) {
        return id == null ? null : shaftRepository.findByIdAndDeletedAtIsNull(id).map(Shaft::getName).orElse(null);
    }

    private String partnerName(Long id) {
        return id == null ? null : partnerRepository.findByIdAndDeletedAtIsNull(id).map(Partner::getLegalName).orElse(null);
    }

    private String contractTypeName(Long id) {
        return id == null ? null : contractTypeRepository.findById(id).map(ContractType::getName).orElse(null);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
