package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.ProductionDtos.CorrectionRequest;
import com.saicomex.dto.ProductionDtos.ProductionDetail;
import com.saicomex.dto.ProductionDtos.ProductionRequest;
import com.saicomex.dto.ProductionDtos.ProductionSummary;
import com.saicomex.dto.ProductionDtos.VerifyRequest;
import com.saicomex.entity.Company;
import com.saicomex.entity.Project;
import com.saicomex.entity.ProductionRecord;
import com.saicomex.entity.Shaft;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.ProductionRecordRepository;
import com.saicomex.repository.ProductionUnitRepository;
import com.saicomex.repository.ProjectRepository;
import com.saicomex.repository.ShaftRepository;
import com.saicomex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * SRS §13, §14 — production capture and its workflow.
 *
 * <p>DRAFT → SUBMITTED → VERIFIED → APPROVED, with REJECTED reachable from
 * SUBMITTED or VERIFIED. The one rule that shapes this whole class is SRS
 * §14: "Production records must not be silently deleted. Corrections must
 * create an audit record." Concretely that means {@link #correct} never
 * touches the figures on the original row — it writes a new row and demotes
 * the original to CORRECTED — and {@link #delete} refuses outright once a
 * record is APPROVED.
 */
@Service
@RequiredArgsConstructor
public class ProductionService {

    private static final Set<String> DELETABLE_STATUSES = Set.of("DRAFT", "SUBMITTED");

    private final ProductionRecordRepository productionRepository;
    private final ProjectRepository projectRepository;
    private final ShaftRepository shaftRepository;
    private final ProductionUnitRepository productionUnitRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;
    private final SystemConfigService systemConfig;

    @Transactional(readOnly = true)
    public PageResponse<ProductionSummary> list(String status, Long projectId, Long shaftId,
                                                LocalDate from, LocalDate to, Pageable pageable) {
        permissions.require("production.view");
        Page<ProductionRecord> page = productionRepository.search(
                blank(status), projectId, shaftId, from, to, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public ProductionDetail get(Long id) {
        permissions.require("production.view");
        ProductionRecord record = load(id);
        permissions.requireShaftAccess(record.getShaftId(), record.getProjectId(), AuditContext.currentRole());
        return toDetail(record);
    }

    /** Paged production history for one shaft — the shaft page's timeline. */
    @Transactional(readOnly = true)
    public PageResponse<ProductionSummary> historyForShaft(Long shaftId, Pageable pageable) {
        permissions.require("production.view");
        Shaft shaft = shaftRepository.findByIdAndDeletedAtIsNull(shaftId)
                .orElseThrow(() -> NotFoundException.of("Shaft", shaftId));
        permissions.requireShaftAccess(shaft.getId(), shaft.getProjectId(), AuditContext.currentRole());
        Page<ProductionRecord> page =
                productionRepository.findAllByShaftIdAndDeletedAtIsNullOrderByProductionDateDesc(shaftId, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    /**
     * Idempotent on {@code clientUuid} (SRS §33): a mobile client that retries
     * an unacknowledged submission gets its own record back instead of a
     * duplicate.
     */
    @Transactional
    public ProductionDetail create(ProductionRequest req) {
        permissions.require("production.create");

        if (req.clientUuid() != null && !req.clientUuid().isBlank()) {
            var existing = productionRepository.findByClientUuidAndDeletedAtIsNull(req.clientUuid());
            if (existing.isPresent()) return toDetail(existing.get());
        }

        Shaft shaft = shaftRepository.findByIdAndDeletedAtIsNull(req.shaftId())
                .orElseThrow(() -> NotFoundException.of("Shaft", req.shaftId()));
        permissions.requireShaftAccess(shaft.getId(), req.projectId(), AuditContext.currentRole());
        projectRepository.findByIdAndDeletedAtIsNull(req.projectId())
                .orElseThrow(() -> NotFoundException.of("Project", req.projectId()));
        validateUnit(req.unitCode());
        validateBackdate(req.productionDate());

        ProductionRecord record = new ProductionRecord();
        record.setCompanyId(defaultCompanyId());
        apply(record, req);
        record.setStatus("DRAFT");
        record.setSource("WEB");
        record.setClientUuid(blank(req.clientUuid()));
        record.setRecordedByUserId(permissions.currentUser().getId());
        record.setVarianceQuantity(variance(req.quantity(), req.targetQuantity()));

        ProductionRecord saved = productionRepository.save(record);
        audit.recordForShaft("CREATE", "PRODUCTION", saved.getId(), label(saved),
                saved.getProjectId(), saved.getShaftId(),
                "Production recorded — " + saved.getQuantity() + " " + saved.getUnitCode()
                + " for " + saved.getProductionDate());
        return toDetail(saved);
    }

    /** Editing is only meaningful before the record has entered the approval chain. */
    @Transactional
    public ProductionDetail update(Long id, ProductionRequest req) {
        permissions.require("production.edit");
        ProductionRecord record = load(id);
        permissions.requireShaftAccess(record.getShaftId(), record.getProjectId(), AuditContext.currentRole());
        if (!"DRAFT".equals(record.getStatus())) {
            throw new BusinessRuleException(
                    "This production record is " + record.getStatus()
                    + " and can no longer be edited directly. Use a correction once it is approved.");
        }
        validateUnit(req.unitCode());
        validateBackdate(req.productionDate());

        audit.recordChange("PRODUCTION", id, label(record), "quantity", record.getQuantity(), req.quantity(), null);

        apply(record, req);
        record.setVarianceQuantity(variance(req.quantity(), req.targetQuantity()));
        return toDetail(productionRepository.save(record));
    }

    @Transactional
    public ProductionDetail submit(Long id) {
        permissions.require("production.edit");
        ProductionRecord record = load(id);
        requireStatus(record, "DRAFT", "submitted for verification");
        record.setStatus("SUBMITTED");
        ProductionRecord saved = productionRepository.save(record);
        audit.recordForShaft("SUBMIT", "PRODUCTION", id, label(saved),
                saved.getProjectId(), saved.getShaftId(), "Submitted for verification");
        return toDetail(saved);
    }

    @Transactional
    public ProductionDetail verify(Long id, VerifyRequest req) {
        permissions.require("production.verify");
        ProductionRecord record = load(id);
        requireStatus(record, "SUBMITTED", "verified");
        record.setStatus("VERIFIED");
        record.setVerifiedByUserId(permissions.currentUser().getId());
        record.setVerifiedAt(LocalDateTime.now());
        ProductionRecord saved = productionRepository.save(record);
        audit.recordForShaft("VERIFY", "PRODUCTION", id, label(saved),
                saved.getProjectId(), saved.getShaftId(),
                "Verified" + (req != null && req.comments() != null ? " — " + req.comments() : ""));
        return toDetail(saved);
    }

    /**
     * VERIFIED is the normal path to APPROVED. When {@code
     * production.require_verification} is switched off, a SUBMITTED record
     * may be approved directly — the config exists precisely to make that
     * skip an operator decision, not a code change.
     */
    @Transactional
    public ProductionDetail approve(Long id) {
        permissions.require("production.approve");
        ProductionRecord record = load(id);
        boolean verificationRequired = systemConfig.getBoolean("production.require_verification", true);
        boolean eligible = "VERIFIED".equals(record.getStatus())
                || (!verificationRequired && "SUBMITTED".equals(record.getStatus()));
        if (!eligible) {
            throw new BusinessRuleException(
                    "This production record is " + record.getStatus() + " and cannot be approved from that status"
                    + (verificationRequired ? " — it must be verified first" : ""));
        }
        record.setStatus("APPROVED");
        record.setApprovedByUserId(permissions.currentUser().getId());
        record.setApprovedAt(LocalDateTime.now());
        ProductionRecord saved = productionRepository.save(record);
        audit.recordForShaft("APPROVE", "PRODUCTION", id, label(saved),
                saved.getProjectId(), saved.getShaftId(), "Approved");
        return toDetail(saved);
    }

    /**
     * SRS §14: a correction is a new row, never an edit of the original. The
     * original moves to CORRECTED with its figures untouched; the new row
     * carries {@code correctsRecordId} back to it and starts life APPROVED so
     * settlement queries (which read {@code status = 'APPROVED'}) see the
     * corrected figure immediately instead of losing it until someone
     * re-approves a DRAFT.
     */
    @Transactional
    public ProductionDetail correct(Long id, CorrectionRequest req) {
        permissions.require("production.approve");
        ProductionRecord original = load(id);
        if (!"APPROVED".equals(original.getStatus())) {
            throw new BusinessRuleException(
                    "Only an APPROVED production record can be corrected (this one is " + original.getStatus()
                    + "). A record that has not yet been approved should simply be edited.");
        }

        ProductionRecord correction = new ProductionRecord();
        correction.setCompanyId(original.getCompanyId());
        correction.setProjectId(original.getProjectId());
        correction.setMiningOperationId(original.getMiningOperationId());
        correction.setShaftId(original.getShaftId());
        correction.setContractId(original.getContractId());
        correction.setProductionDate(original.getProductionDate());
        correction.setShift(original.getShift());
        correction.setPeriodType(original.getPeriodType());
        correction.setOreTonnes(req.oreTonnes() != null ? req.oreTonnes() : original.getOreTonnes());
        correction.setGrade(req.grade() != null ? req.grade() : original.getGrade());
        correction.setRecoveryPercent(original.getRecoveryPercent());
        correction.setGoldRecovered(original.getGoldRecovered());
        correction.setQuantity(req.quantity());
        correction.setUnitCode(original.getUnitCode());
        correction.setProcessingOutput(original.getProcessingOutput());
        correction.setTargetQuantity(original.getTargetQuantity());
        correction.setVarianceQuantity(variance(req.quantity(), original.getTargetQuantity()));
        correction.setStatus("APPROVED");
        correction.setRecordedByUserId(permissions.currentUser().getId());
        correction.setApprovedByUserId(permissions.currentUser().getId());
        correction.setApprovedAt(LocalDateTime.now());
        correction.setCorrectsRecordId(original.getId());
        correction.setCorrectionReason(req.reason());
        correction.setSource("WEB");
        correction.setNotes(original.getNotes());
        ProductionRecord savedCorrection = productionRepository.save(correction);

        BigDecimal oldQuantity = original.getQuantity();
        original.setStatus("CORRECTED");
        productionRepository.save(original);

        audit.recordForShaft("CORRECT", "PRODUCTION", original.getId(), label(original),
                original.getProjectId(), original.getShaftId(),
                "Corrected by record " + savedCorrection.getId() + " — quantity " + oldQuantity
                + " " + original.getUnitCode() + " → " + req.quantity() + " " + original.getUnitCode()
                + " (" + req.reason() + ")");

        return toDetail(savedCorrection);
    }

    /**
     * SRS §14 — never a hard delete, and never at all once APPROVED: from
     * that point the only legitimate way to change the figures is a
     * correction, which keeps the original visible in the trail.
     */
    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("production.delete");
        ProductionRecord record = load(id);
        if (!DELETABLE_STATUSES.contains(record.getStatus())) {
            String why = "APPROVED".equals(record.getStatus())
                    ? "An approved production record must be corrected, not deleted."
                    : "A " + record.getStatus() + " production record cannot be deleted.";
            throw new BusinessRuleException(why + " (record " + id + ")");
        }
        record.softDelete(AuditContext.currentUser());
        productionRepository.save(record);
        audit.recordForShaft("DELETE", "PRODUCTION", id, label(record),
                record.getProjectId(), record.getShaftId(),
                "Deleted" + (reason == null ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- helpers

    private ProductionRecord load(Long id) {
        return productionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("ProductionRecord", id));
    }

    private void requireStatus(ProductionRecord record, String required, String action) {
        if (!required.equals(record.getStatus())) {
            throw new BusinessRuleException(
                    "This production record is " + record.getStatus() + " and cannot be " + action
                    + " from that status (expected " + required + ")");
        }
    }

    private void validateUnit(String unitCode) {
        if (!productionUnitRepository.existsById(unitCode)) {
            throw new BusinessRuleException("Unknown production unit: " + unitCode);
        }
    }

    /**
     * SRS: entering a date further back than the configured window is refused
     * unless the caller already holds approval authority — an approver
     * back-dating a late field report is expected; a field operator quietly
     * rewriting last month is exactly what this guards against.
     */
    private void validateBackdate(LocalDate productionDate) {
        int allowedDays = systemConfig.getInt("production.allow_backdate_days", 7);
        long daysBack = ChronoUnit.DAYS.between(productionDate, LocalDate.now());
        if (daysBack > allowedDays && !permissions.has("production.approve")) {
            throw new BusinessRuleException(
                    "Production date " + productionDate + " is " + daysBack + " days in the past, beyond the "
                    + allowedDays + "-day back-dating window. Only an approver can enter a date this old.");
        }
    }

    private static BigDecimal variance(BigDecimal quantity, BigDecimal target) {
        return target == null ? null : quantity.subtract(target);
    }

    private void apply(ProductionRecord r, ProductionRequest req) {
        r.setProjectId(req.projectId());
        r.setMiningOperationId(req.miningOperationId());
        r.setShaftId(req.shaftId());
        r.setProductionDate(req.productionDate());
        r.setShift(req.shift());
        r.setPeriodType(req.periodType() == null ? "DAILY" : req.periodType());
        r.setOreTonnes(req.oreTonnes());
        r.setGrade(req.grade());
        r.setRecoveryPercent(req.recoveryPercent());
        r.setGoldRecovered(req.goldRecovered());
        r.setQuantity(req.quantity());
        r.setUnitCode(req.unitCode());
        r.setProcessingOutput(req.processingOutput());
        r.setTargetQuantity(req.targetQuantity());
        r.setNotes(req.notes());
    }

    private ProductionSummary toSummary(ProductionRecord r) {
        return new ProductionSummary(
                r.getId(), projectName(r.getProjectId()), shaftName(r.getShaftId()), r.getProductionDate(),
                r.getShift(), r.getPeriodType(), r.getQuantity(), r.getUnitCode(),
                r.getTargetQuantity(), r.getVarianceQuantity(), r.getStatus());
    }

    private ProductionDetail toDetail(ProductionRecord r) {
        return new ProductionDetail(
                r.getId(), r.getProjectId(), projectName(r.getProjectId()), r.getMiningOperationId(),
                r.getShaftId(), shaftName(r.getShaftId()), r.getProductionDate(), r.getShift(), r.getPeriodType(),
                r.getOreTonnes(), r.getGrade(), r.getRecoveryPercent(), r.getGoldRecovered(),
                r.getQuantity(), r.getUnitCode(), r.getProcessingOutput(), r.getTargetQuantity(),
                r.getVarianceQuantity(), r.getStatus(),
                userName(r.getRecordedByUserId()), userName(r.getVerifiedByUserId()), r.getVerifiedAt(),
                userName(r.getApprovedByUserId()), r.getApprovedAt(),
                r.getCorrectsRecordId(), r.getCorrectionReason(), r.getSource(), r.getClientUuid(),
                r.getNotes(), r.getCreatedAt(), r.getCreatedBy());
    }

    private String label(ProductionRecord r) {
        return r.getProductionDate() + " " + r.getQuantity() + " " + r.getUnitCode();
    }

    private String projectName(Long id) {
        return id == null ? null : projectRepository.findById(id).map(Project::getName).orElse(null);
    }

    private String shaftName(Long id) {
        return id == null ? null : shaftRepository.findById(id).map(Shaft::getName).orElse(null);
    }

    private String userName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(u -> u.getFirstName() + " " + u.getLastName()).orElse(null);
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private static String blank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
