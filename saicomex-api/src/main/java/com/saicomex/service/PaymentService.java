package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.PaymentDtos.PaymentDetail;
import com.saicomex.dto.PaymentDtos.PaymentRequest;
import com.saicomex.dto.PaymentDtos.PaymentSummary;
import com.saicomex.entity.Company;
import com.saicomex.entity.LedgerEntry;
import com.saicomex.entity.Partner;
import com.saicomex.entity.Payment;
import com.saicomex.entity.Project;
import com.saicomex.entity.Settlement;
import com.saicomex.entity.Shaft;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.ExpenseRepository;
import com.saicomex.repository.LedgerEntryRepository;
import com.saicomex.repository.PartnerRepository;
import com.saicomex.repository.PaymentRepository;
import com.saicomex.repository.ProjectRepository;
import com.saicomex.repository.SettlementRepository;
import com.saicomex.repository.ShaftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * SRS §27 — payments to partners, suppliers, employees and contractors.
 *
 * <p>The one rule worth reading twice is in {@link #markPaid}: paying against
 * a settlement moves money in two places at once, the payment and the
 * settlement it is discharging, and the two must never disagree. A payment
 * can never push a settlement's {@code amountPaid} past its {@code
 * partnerNetPayable} — that would mean the partner statement says less is
 * owed than has actually gone out the door.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Set<String> PAYMENT_TYPES = Set.of("PARTNER", "SUPPLIER", "EMPLOYEE", "CONTRACTOR", "OTHER");
    private static final Set<String> DELETABLE_STATUSES = Set.of("DRAFT", "REJECTED");

    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final PartnerRepository partnerRepository;
    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;
    private final ShaftRepository shaftRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<PaymentSummary> list(String status, String paymentType, Long partnerId, Long projectId,
                                             Long shaftId, LocalDate from, LocalDate to, Pageable pageable) {
        permissions.require("payments.view");
        Page<Payment> page = paymentRepository.search(
                blank(status), blank(paymentType), partnerId, projectId, shaftId, from, to, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public PaymentDetail get(Long id) {
        permissions.require("payments.view");
        Payment payment = load(id);
        requireScope(payment);
        return toDetail(payment);
    }

    @Transactional
    public PaymentDetail create(PaymentRequest req) {
        permissions.require("payments.create");
        if (!PAYMENT_TYPES.contains(req.paymentType())) {
            throw new BusinessRuleException("Unknown payment type: " + req.paymentType());
        }
        if (req.partnerId() != null) {
            partnerRepository.findByIdAndDeletedAtIsNull(req.partnerId())
                    .orElseThrow(() -> NotFoundException.of("Partner", req.partnerId()));
        }
        if (req.settlementId() != null) {
            settlementRepository.findByIdAndDeletedAtIsNull(req.settlementId())
                    .orElseThrow(() -> NotFoundException.of("Settlement", req.settlementId()));
        }
        if (req.expenseId() != null) {
            expenseRepository.findByIdAndDeletedAtIsNull(req.expenseId())
                    .orElseThrow(() -> NotFoundException.of("Expense", req.expenseId()));
        }
        if (req.projectId() != null) {
            requireScope(req.projectId(), req.shaftId());
        }

        Payment payment = new Payment();
        payment.setCompanyId(defaultCompanyId());
        payment.setPaymentNumber(nextPaymentNumber());
        apply(payment, req);
        // The rate is frozen here, at entry — a historical payment figure
        // never moves when today's exchange rate changes.
        payment.setBaseAmount(req.amount().multiply(req.exchangeRate()).setScale(4, RoundingMode.HALF_UP));
        payment.setStatus("DRAFT");
        Payment saved = paymentRepository.save(payment);

        audit.record("CREATE", "PAYMENT", saved.getId(), saved.getPaymentNumber(),
                "Payment " + saved.getPaymentNumber() + " created — " + saved.getBaseAmount() + " (base) to " + saved.getRecipientName());
        return toDetail(saved);
    }

    /** Editing is only permitted while DRAFT — once approval has started, cancel and re-create instead. */
    @Transactional
    public PaymentDetail update(Long id, PaymentRequest req) {
        permissions.require("payments.edit");
        Payment payment = load(id);
        requireScope(payment);
        if (!"DRAFT".equals(payment.getStatus())) {
            throw new BusinessRuleException(
                    "This payment is " + payment.getStatus() + " and can no longer be edited");
        }
        if (!PAYMENT_TYPES.contains(req.paymentType())) {
            throw new BusinessRuleException("Unknown payment type: " + req.paymentType());
        }

        audit.recordChange("PAYMENT", id, payment.getPaymentNumber(), "amount", payment.getAmount(), req.amount(), null);

        apply(payment, req);
        payment.setBaseAmount(req.amount().multiply(req.exchangeRate()).setScale(4, RoundingMode.HALF_UP));
        return toDetail(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentDetail approve(Long id) {
        permissions.require("payments.approve");
        Payment payment = load(id);
        if (!"DRAFT".equals(payment.getStatus())) {
            throw new BusinessRuleException(
                    "This payment is " + payment.getStatus() + " and cannot be approved from that status");
        }
        payment.setStatus("APPROVED");
        payment.setApprovedBy(AuditContext.currentUser());
        payment.setApprovedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        audit.record("APPROVE", "PAYMENT", id, saved.getPaymentNumber(), "Approved");
        return toDetail(saved);
    }

    /**
     * Disburses an approved payment. When it discharges a settlement, the
     * settlement's {@code amountPaid} / {@code amountOutstanding} / {@code
     * status} are updated in the same transaction as the payment — the two
     * records must never be visible to a reader in a state where they
     * disagree about how much of the settlement has been paid.
     */
    @Transactional
    public PaymentDetail markPaid(Long id) {
        permissions.require("payments.approve");
        Payment payment = load(id);
        if (!"APPROVED".equals(payment.getStatus())) {
            throw new BusinessRuleException(
                    "This payment is " + payment.getStatus() + " and cannot be marked paid — approve it first");
        }

        if (payment.getSettlementId() != null) {
            Settlement settlement = settlementRepository.findByIdAndDeletedAtIsNull(payment.getSettlementId())
                    .orElseThrow(() -> NotFoundException.of("Settlement", payment.getSettlementId()));

            BigDecimal previouslyPaid = nz(settlement.getAmountPaid());
            BigDecimal newAmountPaid = previouslyPaid.add(payment.getBaseAmount());
            if (newAmountPaid.compareTo(settlement.getPartnerNetPayable()) > 0) {
                BigDecimal remaining = settlement.getPartnerNetPayable().subtract(previouslyPaid);
                throw new BusinessRuleException(
                        "This payment would take the settlement's amount paid above what is payable. "
                        + "Only " + remaining + " " + settlement.getCurrency() + " remains outstanding on "
                        + settlement.getSettlementNumber());
            }

            settlement.setAmountPaid(newAmountPaid);
            BigDecimal outstanding = settlement.getPartnerNetPayable().subtract(newAmountPaid);
            settlement.setAmountOutstanding(outstanding);
            settlement.setStatus(outstanding.signum() <= 0 ? "PAID" : "PARTIALLY_PAID");
            settlementRepository.save(settlement);

            audit.recordForShaft("PAYMENT_APPLIED", "SETTLEMENT", settlement.getId(), settlement.getSettlementNumber(),
                    settlement.getProjectId(), settlement.getShaftId(),
                    "Payment " + payment.getPaymentNumber() + " applied — amount paid now " + newAmountPaid
                    + ", outstanding " + outstanding);
        }

        payment.setStatus("PAID");
        Payment saved = paymentRepository.save(payment);

        writeLedgerEntry(saved);

        audit.record("MARK_PAID", "PAYMENT", id, saved.getPaymentNumber(), "Marked as paid");
        return toDetail(saved);
    }

    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("payments.delete");
        Payment payment = load(id);
        requireScope(payment);
        if (!DELETABLE_STATUSES.contains(payment.getStatus())) {
            throw new BusinessRuleException(
                    "This payment is " + payment.getStatus() + " and cannot be deleted");
        }
        payment.softDelete(AuditContext.currentUser());
        paymentRepository.save(payment);
        audit.record("DELETE", "PAYMENT", id, payment.getPaymentNumber(),
                "Deleted" + (reason == null ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- helpers

    private Payment load(Long id) {
        return paymentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Payment", id));
    }

    private void requireScope(Payment payment) {
        requireScope(payment.getProjectId(), payment.getShaftId());
    }

    private void requireScope(Long projectId, Long shaftId) {
        if (projectId == null) return;
        if (shaftId != null) {
            permissions.requireShaftAccess(shaftId, projectId, AuditContext.currentRole());
        } else {
            permissions.requireProjectAccess(projectId, AuditContext.currentRole());
        }
    }

    private void writeLedgerEntry(Payment payment) {
        LedgerEntry entry = new LedgerEntry();
        entry.setCompanyId(payment.getCompanyId());
        entry.setEntryDate(payment.getPaymentDate());
        entry.setEntryType("PAYMENT");
        entry.setDirection("DEBIT");
        entry.setProjectId(payment.getProjectId());
        entry.setMiningOperationId(payment.getMiningOperationId());
        entry.setShaftId(payment.getShaftId());
        entry.setPartnerId(payment.getPartnerId());
        entry.setCategoryId(payment.getCategoryId());
        entry.setDescription(payment.getPaymentNumber() + " — " + payment.getRecipientName());
        entry.setAmount(payment.getAmount());
        entry.setCurrency(payment.getCurrency());
        entry.setExchangeRate(payment.getExchangeRate());
        entry.setBaseAmount(payment.getBaseAmount());
        entry.setSourceTable("payments");
        entry.setSourceId(payment.getId());
        ledgerRepository.save(entry);
    }

    private void apply(Payment p, PaymentRequest r) {
        p.setPaymentType(r.paymentType());
        p.setPaymentDate(r.paymentDate());
        p.setPartnerId(r.partnerId());
        p.setSupplierId(r.supplierId());
        p.setEmployeeId(r.employeeId());
        p.setRecipientName(r.recipientName());
        p.setProjectId(r.projectId());
        p.setMiningOperationId(r.miningOperationId());
        p.setShaftId(r.shaftId());
        p.setSettlementId(r.settlementId());
        p.setExpenseId(r.expenseId());
        p.setCategoryId(r.categoryId());
        p.setAmount(r.amount());
        p.setCurrency(r.currency());
        p.setExchangeRate(r.exchangeRate());
        p.setPaymentMethod(r.paymentMethod());
        p.setBankReference(r.bankReference());
        p.setReference(r.reference());
        p.setNotes(r.notes());
    }

    private PaymentSummary toSummary(Payment p) {
        return new PaymentSummary(p.getId(), p.getPaymentNumber(), p.getPaymentType(), p.getPaymentDate(),
                p.getRecipientName(), p.getAmount(), p.getCurrency(), p.getBaseAmount(), p.getStatus());
    }

    private PaymentDetail toDetail(Payment p) {
        return new PaymentDetail(
                p.getId(), p.getPaymentNumber(), p.getPaymentType(), p.getPaymentDate(),
                p.getPartnerId(), partnerName(p.getPartnerId()), p.getSupplierId(), p.getEmployeeId(),
                p.getRecipientName(), p.getProjectId(), projectName(p.getProjectId()), p.getMiningOperationId(),
                p.getShaftId(), shaftName(p.getShaftId()), p.getSettlementId(), settlementNumber(p.getSettlementId()),
                p.getExpenseId(), p.getCategoryId(), p.getAmount(), p.getCurrency(), p.getExchangeRate(),
                p.getBaseAmount(), p.getPaymentMethod(), p.getBankReference(), p.getReference(), p.getStatus(),
                p.getApprovedBy(), p.getApprovedAt(), p.getNotes(), p.getCreatedAt(), p.getCreatedBy());
    }

    private String projectName(Long id) {
        return id == null ? null : projectRepository.findById(id).map(Project::getName).orElse(null);
    }

    private String shaftName(Long id) {
        return id == null ? null : shaftRepository.findById(id).map(Shaft::getName).orElse(null);
    }

    private String partnerName(Long id) {
        return id == null ? null : partnerRepository.findById(id).map(Partner::getLegalName).orElse(null);
    }

    private String settlementNumber(Long id) {
        return id == null ? null : settlementRepository.findById(id).map(Settlement::getSettlementNumber).orElse(null);
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private String nextPaymentNumber() {
        String prefix = "PAY-" + LocalDate.now().getYear() + "-";
        long count = paymentRepository.count() + 1;
        String candidate = prefix + String.format("%05d", count);
        while (paymentRepository.existsByPaymentNumberIgnoreCaseAndDeletedAtIsNull(candidate)) {
            count++;
            candidate = prefix + String.format("%05d", count);
        }
        return candidate;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
