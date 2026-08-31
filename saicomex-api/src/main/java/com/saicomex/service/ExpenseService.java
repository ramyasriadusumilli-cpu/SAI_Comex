package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.ExpenseDtos.AllocationDto;
import com.saicomex.dto.ExpenseDtos.AllocationRequest;
import com.saicomex.dto.ExpenseDtos.ApprovalDecision;
import com.saicomex.dto.ExpenseDtos.ExpenseDetail;
import com.saicomex.dto.ExpenseDtos.ExpenseRequest;
import com.saicomex.dto.ExpenseDtos.ExpenseSummary;
import com.saicomex.dto.ExpenseDtos.RejectionRequest;
import com.saicomex.dto.PageResponse;
import com.saicomex.entity.ApprovalThreshold;
import com.saicomex.entity.Company;
import com.saicomex.entity.Expense;
import com.saicomex.entity.ExpenseAllocation;
import com.saicomex.entity.ExpenseCategory;
import com.saicomex.entity.LedgerEntry;
import com.saicomex.entity.Project;
import com.saicomex.entity.Shaft;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.ApprovalThresholdRepository;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.ExpenseAllocationRepository;
import com.saicomex.repository.ExpenseCategoryRepository;
import com.saicomex.repository.ExpenseRepository;
import com.saicomex.repository.LedgerEntryRepository;
import com.saicomex.repository.ProjectRepository;
import com.saicomex.repository.ShaftRepository;
import com.saicomex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SRS §15, §16 — expenses, their shaft allocation, and approval routing.
 *
 * <p>Two design decisions carry the whole class. First, {@link
 * #createAllocations} writes at least one {@code expense_allocations} row for
 * every expense, DIRECT included — so the settlement engine and every cost
 * report read one table and never special-case a single-shaft expense.
 * Second, {@link #resolveThreshold} always reads {@code approval_thresholds}
 * rather than hard-coding a band: SRS §16 requires the routing to be
 * configurable, and a hard-coded band here would make that requirement a lie.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal PERCENT_TOLERANCE = new BigDecimal("0.01");
    private static final List<String> EDITABLE_STATUSES = List.of("DRAFT", "REJECTED");

    private final ExpenseRepository expenseRepository;
    private final ExpenseAllocationRepository allocationRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ApprovalThresholdRepository thresholdRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final ProjectRepository projectRepository;
    private final ShaftRepository shaftRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<ExpenseSummary> list(String status, Long projectId, Long shaftId, Long categoryId,
                                             LocalDate from, LocalDate to, String search, Pageable pageable) {
        permissions.require("expenses.view");
        Page<Expense> page = expenseRepository.search(
                blank(status), projectId, shaftId, categoryId, from, to, blank(search), pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public ExpenseDetail get(Long id) {
        permissions.require("expenses.view");
        Expense expense = load(id);
        requireScope(expense);
        return toDetail(expense);
    }

    @Transactional
    public ExpenseDetail create(ExpenseRequest req) {
        permissions.require("expenses.create");

        if (req.clientUuid() != null && !req.clientUuid().isBlank()) {
            var existing = expenseRepository.findByClientUuidAndDeletedAtIsNull(req.clientUuid());
            if (existing.isPresent()) return toDetail(existing.get());
        }

        projectRepository.findByIdAndDeletedAtIsNull(req.projectId())
                .orElseThrow(() -> NotFoundException.of("Project", req.projectId()));
        if (req.shaftId() != null) {
            Shaft shaft = shaftRepository.findByIdAndDeletedAtIsNull(req.shaftId())
                    .orElseThrow(() -> NotFoundException.of("Shaft", req.shaftId()));
            permissions.requireShaftAccess(shaft.getId(), req.projectId(), AuditContext.currentRole());
        } else {
            permissions.requireProjectAccess(req.projectId(), AuditContext.currentRole());
        }
        categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> NotFoundException.of("ExpenseCategory", req.categoryId()));
        if ("DIRECT".equals(allocationMethodOf(req)) && req.shaftId() == null) {
            throw new BusinessRuleException("A DIRECT expense must specify a shaft");
        }

        Expense expense = new Expense();
        expense.setCompanyId(defaultCompanyId());
        expense.setExpenseNumber(nextExpenseNumber());
        apply(expense, req);
        // The rate is frozen on the row at the moment of entry, so a historical
        // expense figure never moves when today's exchange rate changes.
        expense.setBaseAmount(baseAmountOf(req.amount(), req.exchangeRate()));
        expense.setStatus("DRAFT");
        expense.setSource("WEB");
        expense.setClientUuid(blank(req.clientUuid()));
        Expense saved = expenseRepository.save(expense);

        createAllocations(saved, req.allocations());

        audit.record("CREATE", "EXPENSE", saved.getId(), saved.getExpenseNumber(),
                "Expense " + saved.getExpenseNumber() + " created — " + saved.getBaseAmount() + " (base amount)");
        return toDetail(saved);
    }

    /** Editing is only permitted while DRAFT or REJECTED. */
    @Transactional
    public ExpenseDetail update(Long id, ExpenseRequest req) {
        permissions.require("expenses.edit");
        Expense expense = load(id);
        requireScope(expense);
        if (!EDITABLE_STATUSES.contains(expense.getStatus())) {
            throw new BusinessRuleException(
                    "This expense is " + expense.getStatus() + " and can no longer be edited");
        }
        categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> NotFoundException.of("ExpenseCategory", req.categoryId()));
        if ("DIRECT".equals(allocationMethodOf(req)) && req.shaftId() == null) {
            throw new BusinessRuleException("A DIRECT expense must specify a shaft");
        }

        audit.recordChange("EXPENSE", id, expense.getExpenseNumber(), "amount", expense.getAmount(), req.amount(), null);

        boolean wasRejected = "REJECTED".equals(expense.getStatus());
        apply(expense, req);
        expense.setBaseAmount(baseAmountOf(req.amount(), req.exchangeRate()));
        if (wasRejected) {
            // Re-entering the workflow from scratch — the old rejection no
            // longer describes this version of the expense.
            expense.setStatus("DRAFT");
            expense.setRejectionReason(null);
            expense.setApprovalStage(null);
        }
        Expense saved = expenseRepository.save(expense);

        allocationRepository.deleteAllByExpenseId(id);
        createAllocations(saved, req.allocations());

        return toDetail(saved);
    }

    @Transactional
    public ExpenseDetail submit(Long id) {
        permissions.require("expenses.edit");
        Expense expense = load(id);
        requireScope(expense);
        if (!EDITABLE_STATUSES.contains(expense.getStatus())) {
            throw new BusinessRuleException(
                    "This expense is " + expense.getStatus() + " and cannot be submitted from that status");
        }
        ApprovalThreshold threshold = resolveThreshold(expense);
        expense.setApprovalStage(threshold.getRequiredRole());
        expense.setStatus("PENDING_APPROVAL");
        expense.setSubmittedByUserId(permissions.currentUser().getId());
        expense.setRejectionReason(null);
        Expense saved = expenseRepository.save(expense);

        audit.record("SUBMIT", "EXPENSE", id, saved.getExpenseNumber(),
                "Submitted for approval — routed to " + threshold.getRequiredRole()
                + " (threshold step " + threshold.getStepNo() + ")");
        return toDetail(saved);
    }

    @Transactional
    public ExpenseDetail approve(Long id, ApprovalDecision req) {
        permissions.require("expenses.approve");
        Expense expense = load(id);
        if (!"PENDING_APPROVAL".equals(expense.getStatus())) {
            throw new BusinessRuleException(
                    "This expense is " + expense.getStatus() + " and is not awaiting approval");
        }
        String role = AuditContext.currentRole();
        boolean authorised = (expense.getApprovalStage() != null && expense.getApprovalStage().equalsIgnoreCase(role))
                || "DIRECTOR".equals(role) || "EXECUTIVE".equals(role);
        if (!authorised) {
            throw new AccessDeniedException(
                    "This expense requires approval from " + expense.getApprovalStage()
                    + " (or a Director/Executive) — your role is " + role);
        }

        expense.setStatus("APPROVED");
        expense.setApprovedByUserId(permissions.currentUser().getId());
        expense.setApprovedAt(LocalDateTime.now());
        Expense saved = expenseRepository.save(expense);

        writeLedgerEntry(saved);

        audit.record("APPROVE", "EXPENSE", id, saved.getExpenseNumber(),
                "Approved" + (req != null && req.comments() != null ? " — " + req.comments() : ""));
        return toDetail(saved);
    }

    @Transactional
    public ExpenseDetail reject(Long id, RejectionRequest req) {
        permissions.require("expenses.approve");
        Expense expense = load(id);
        if (!"PENDING_APPROVAL".equals(expense.getStatus())) {
            throw new BusinessRuleException(
                    "This expense is " + expense.getStatus() + " and is not awaiting approval");
        }
        expense.setStatus("REJECTED");
        expense.setRejectionReason(req.reason());
        Expense saved = expenseRepository.save(expense);

        audit.record("REJECT", "EXPENSE", id, saved.getExpenseNumber(), "Rejected — " + req.reason());
        return toDetail(saved);
    }

    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("expenses.delete");
        Expense expense = load(id);
        if (!EDITABLE_STATUSES.contains(expense.getStatus())) {
            throw new BusinessRuleException(
                    "This expense is " + expense.getStatus()
                    + " and cannot be deleted — take it through the approval workflow instead");
        }
        expense.softDelete(AuditContext.currentUser());
        expenseRepository.save(expense);
        audit.record("DELETE", "EXPENSE", id, expense.getExpenseNumber(),
                "Deleted" + (reason == null ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- allocation

    /**
     * Always writes at least one row. DIRECT builds its single 100% row from
     * the expense's own shaft; every other method reads the caller-supplied
     * lines and requires them to add up to 100% (0.01 tolerance) across
     * shafts that actually exist.
     */
    private void createAllocations(Expense expense, List<AllocationRequest> lines) {
        if ("DIRECT".equals(expense.getAllocationMethod())) {
            ExpenseAllocation allocation = new ExpenseAllocation();
            allocation.setExpenseId(expense.getId());
            allocation.setProjectId(expense.getProjectId());
            allocation.setMiningOperationId(expense.getMiningOperationId());
            allocation.setShaftId(expense.getShaftId());
            allocation.setAllocationPercent(HUNDRED);
            allocation.setAmount(expense.getAmount());
            allocation.setBaseAmount(expense.getBaseAmount());
            allocation.setBasisNote("Direct expense — single shaft");
            allocationRepository.save(allocation);
            return;
        }

        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException(
                    "Allocation method " + expense.getAllocationMethod() + " requires at least one allocation line");
        }

        BigDecimal totalPercent = BigDecimal.ZERO;
        for (AllocationRequest line : lines) {
            Shaft shaft = shaftRepository.findByIdAndDeletedAtIsNull(line.shaftId())
                    .orElseThrow(() -> NotFoundException.of("Shaft", line.shaftId()));
            if (line.allocationPercent() == null && line.amount() == null) {
                throw new BusinessRuleException(
                        "Allocation for shaft " + shaft.getName() + " needs either a percentage or an amount");
            }
            BigDecimal percent = line.allocationPercent() != null
                    ? line.allocationPercent()
                    : line.amount().multiply(HUNDRED).divide(expense.getAmount(), 6, RoundingMode.HALF_UP);
            BigDecimal amount = line.amount() != null
                    ? line.amount()
                    : expense.getAmount().multiply(percent).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            totalPercent = totalPercent.add(percent);

            ExpenseAllocation allocation = new ExpenseAllocation();
            allocation.setExpenseId(expense.getId());
            allocation.setProjectId(expense.getProjectId());
            allocation.setMiningOperationId(expense.getMiningOperationId());
            allocation.setShaftId(shaft.getId());
            allocation.setAllocationPercent(percent);
            allocation.setAllocationQuantity(line.allocationQuantity());
            allocation.setAmount(amount);
            allocation.setBaseAmount(baseAmountOf(amount, expense.getExchangeRate()));
            allocation.setBasisNote(line.basisNote());
            allocationRepository.save(allocation);
        }

        if (totalPercent.subtract(HUNDRED).abs().compareTo(PERCENT_TOLERANCE) > 0) {
            throw new BusinessRuleException(
                    "Allocations total " + totalPercent + "%, not 100% (tolerance " + PERCENT_TOLERANCE + "%)");
        }
    }

    /**
     * SRS §16 — never a hard-coded band. Matched by expense class (OPEX/CAPEX,
     * where the threshold row specifies one) and the {@code [minAmount,
     * maxAmount)} band containing the expense's base amount.
     */
    private ApprovalThreshold resolveThreshold(Expense expense) {
        String expenseClass = categoryRepository.findById(expense.getCategoryId())
                .map(ExpenseCategory::getExpenseClass).orElse(null);
        List<ApprovalThreshold> thresholds =
                thresholdRepository.findAllByEntityTypeAndIsActiveTrueOrderByStepNoAscMinAmountAsc("EXPENSE");
        for (ApprovalThreshold t : thresholds) {
            if (t.getExpenseClass() != null && !t.getExpenseClass().equalsIgnoreCase(expenseClass)) continue;
            boolean aboveMin = expense.getBaseAmount().compareTo(t.getMinAmount()) >= 0;
            boolean belowMax = t.getMaxAmount() == null || expense.getBaseAmount().compareTo(t.getMaxAmount()) < 0;
            if (aboveMin && belowMax) return t;
        }
        throw new BusinessRuleException(
                "No active approval threshold covers an expense of " + expense.getBaseAmount()
                + " — configure one under Approval Thresholds before this can be submitted");
    }

    private void writeLedgerEntry(Expense expense) {
        LedgerEntry entry = new LedgerEntry();
        entry.setCompanyId(expense.getCompanyId());
        entry.setEntryDate(expense.getExpenseDate());
        entry.setEntryType("EXPENSE");
        entry.setDirection("DEBIT");
        entry.setProjectId(expense.getProjectId());
        entry.setMiningOperationId(expense.getMiningOperationId());
        entry.setShaftId(expense.getShaftId());
        entry.setCategoryId(expense.getCategoryId());
        entry.setDescription(expense.getExpenseNumber() + " — " + expense.getDescription());
        entry.setAmount(expense.getAmount());
        entry.setCurrency(expense.getCurrency());
        entry.setExchangeRate(expense.getExchangeRate());
        entry.setBaseAmount(expense.getBaseAmount());
        entry.setSourceTable("expenses");
        entry.setSourceId(expense.getId());
        ledgerRepository.save(entry);
    }

    // ---------------------------------------------------------------- helpers

    private Expense load(Long id) {
        return expenseRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Expense", id));
    }

    private void requireScope(Expense expense) {
        if (expense.getShaftId() != null) {
            permissions.requireShaftAccess(expense.getShaftId(), expense.getProjectId(), AuditContext.currentRole());
        } else {
            permissions.requireProjectAccess(expense.getProjectId(), AuditContext.currentRole());
        }
    }

    private static String allocationMethodOf(ExpenseRequest req) {
        return req.allocationMethod() == null ? "DIRECT" : req.allocationMethod();
    }

    private static BigDecimal baseAmountOf(BigDecimal amount, BigDecimal exchangeRate) {
        return amount.multiply(exchangeRate).setScale(4, RoundingMode.HALF_UP);
    }

    private void apply(Expense e, ExpenseRequest r) {
        e.setProjectId(r.projectId());
        e.setMiningOperationId(r.miningOperationId());
        e.setShaftId(r.shaftId());
        e.setCategoryId(r.categoryId());
        e.setSupplierId(r.supplierId());
        e.setExpenseDate(r.expenseDate());
        e.setDescription(r.description());
        e.setQuantity(r.quantity());
        e.setUnit(r.unit());
        e.setUnitCost(r.unitCost());
        e.setAmount(r.amount());
        e.setCurrency(r.currency());
        e.setExchangeRate(r.exchangeRate());
        e.setTaxAmount(r.taxAmount() == null ? BigDecimal.ZERO : r.taxAmount());
        e.setAllocationMethod(allocationMethodOf(r));
        e.setIsShared(r.isShared() != null && r.isShared());
        e.setReference(r.reference());
        e.setInvoiceNumber(r.invoiceNumber());
        e.setPaymentMethod(r.paymentMethod());
        e.setNotes(r.notes());
    }

    private ExpenseSummary toSummary(Expense e) {
        return new ExpenseSummary(
                e.getId(), e.getExpenseNumber(), projectName(e.getProjectId()), shaftName(e.getShaftId()),
                categoryName(e.getCategoryId()), e.getExpenseDate(), e.getDescription(),
                e.getAmount(), e.getCurrency(), e.getBaseAmount(), e.getStatus(), e.getApprovalStage());
    }

    private ExpenseDetail toDetail(Expense e) {
        List<AllocationDto> allocations = allocationRepository.findAllByExpenseId(e.getId()).stream()
                .map(a -> new AllocationDto(a.getId(), a.getShaftId(), shaftName(a.getShaftId()),
                        a.getAllocationPercent(), a.getAllocationQuantity(), a.getAmount(), a.getBaseAmount(), a.getBasisNote()))
                .toList();

        return new ExpenseDetail(
                e.getId(), e.getExpenseNumber(), e.getProjectId(), projectName(e.getProjectId()),
                e.getMiningOperationId(), e.getShaftId(), shaftName(e.getShaftId()),
                e.getCategoryId(), categoryName(e.getCategoryId()), e.getSupplierId(),
                e.getExpenseDate(), e.getDescription(), e.getQuantity(), e.getUnit(), e.getUnitCost(),
                e.getAmount(), e.getCurrency(), e.getExchangeRate(), e.getBaseAmount(), e.getTaxAmount(),
                e.getAllocationMethod(), Boolean.TRUE.equals(e.getIsShared()),
                e.getReference(), e.getInvoiceNumber(), e.getPaymentMethod(),
                e.getStatus(), e.getApprovalStage(), userName(e.getSubmittedByUserId()), userName(e.getApprovedByUserId()),
                e.getApprovedAt(), e.getRejectionReason(), e.getPaidAt(), e.getNotes(), e.getCreatedAt(), e.getCreatedBy(),
                allocations);
    }

    private String userName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(u -> u.getFirstName() + " " + u.getLastName()).orElse(null);
    }

    private String projectName(Long id) {
        return id == null ? null : projectRepository.findById(id).map(Project::getName).orElse(null);
    }

    private String shaftName(Long id) {
        return id == null ? null : shaftRepository.findById(id).map(Shaft::getName).orElse(null);
    }

    private String categoryName(Long id) {
        return id == null ? null : categoryRepository.findById(id).map(ExpenseCategory::getName).orElse(null);
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private String nextExpenseNumber() {
        String prefix = "EXP-" + LocalDate.now().getYear() + "-";
        long count = expenseRepository.count() + 1;
        String candidate = prefix + String.format("%05d", count);
        while (expenseRepository.existsByExpenseNumberIgnoreCaseAndDeletedAtIsNull(candidate)) {
            count++;
            candidate = prefix + String.format("%05d", count);
        }
        return candidate;
    }

    private static String blank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
