package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.SaleDtos.SaleDetail;
import com.saicomex.dto.SaleDtos.SaleRequest;
import com.saicomex.dto.SaleDtos.SaleSummary;
import com.saicomex.entity.Buyer;
import com.saicomex.entity.Company;
import com.saicomex.entity.LedgerEntry;
import com.saicomex.entity.Project;
import com.saicomex.entity.Sale;
import com.saicomex.entity.Shaft;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.BuyerRepository;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.LedgerEntryRepository;
import com.saicomex.repository.ProductionUnitRepository;
import com.saicomex.repository.ProjectRepository;
import com.saicomex.repository.SaleRepository;
import com.saicomex.repository.ShaftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * SRS §23 — revenue capture. A sale becomes a real revenue figure only on
 * {@link #confirm}; DRAFT exists so a data-entry mistake never reaches the
 * ledger. Once a sale has been consumed by a settlement ({@code
 * settlementStatus = SETTLED}) neither editing nor cancelling it is
 * permitted — the partner statement built from it has already been computed
 * and, possibly, paid.
 */
@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProjectRepository projectRepository;
    private final ShaftRepository shaftRepository;
    private final BuyerRepository buyerRepository;
    private final ProductionUnitRepository productionUnitRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<SaleSummary> list(String status, Long projectId, Long shaftId, Long buyerId,
                                          LocalDate from, LocalDate to, Pageable pageable) {
        permissions.require("sales.view");
        Page<Sale> page = saleRepository.search(blank(status), projectId, shaftId, buyerId, from, to, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public SaleDetail get(Long id) {
        permissions.require("sales.view");
        Sale sale = load(id);
        requireScope(sale);
        return toDetail(sale);
    }

    @Transactional
    public SaleDetail create(SaleRequest req) {
        permissions.require("sales.create");
        projectRepository.findByIdAndDeletedAtIsNull(req.projectId())
                .orElseThrow(() -> NotFoundException.of("Project", req.projectId()));
        if (req.shaftId() != null) {
            Shaft shaft = shaftRepository.findByIdAndDeletedAtIsNull(req.shaftId())
                    .orElseThrow(() -> NotFoundException.of("Shaft", req.shaftId()));
            permissions.requireShaftAccess(shaft.getId(), req.projectId(), AuditContext.currentRole());
        } else {
            permissions.requireProjectAccess(req.projectId(), AuditContext.currentRole());
        }
        validateUnit(req.unitCode());
        if (req.buyerId() != null) {
            buyerRepository.findByIdAndDeletedAtIsNull(req.buyerId())
                    .orElseThrow(() -> NotFoundException.of("Buyer", req.buyerId()));
        }

        Sale sale = new Sale();
        sale.setCompanyId(defaultCompanyId());
        sale.setSaleNumber(nextSaleNumber());
        apply(sale, req);
        computeAmounts(sale);
        sale.setStatus("DRAFT");
        Sale saved = saleRepository.save(sale);

        audit.record("CREATE", "SALE", saved.getId(), saved.getSaleNumber(),
                "Sale " + saved.getSaleNumber() + " recorded — " + saved.getQuantity() + " " + saved.getUnitCode());
        return toDetail(saved);
    }

    /** Editing is only permitted while DRAFT. */
    @Transactional
    public SaleDetail update(Long id, SaleRequest req) {
        permissions.require("sales.edit");
        Sale sale = load(id);
        requireScope(sale);
        requireEditable(sale);
        validateUnit(req.unitCode());

        audit.recordChange("SALE", id, sale.getSaleNumber(), "quantity", sale.getQuantity(), req.quantity(), null);

        apply(sale, req);
        computeAmounts(sale);
        return toDetail(saleRepository.save(sale));
    }

    @Transactional
    public SaleDetail confirm(Long id) {
        permissions.require("sales.approve");
        Sale sale = load(id);
        requireScope(sale);
        if (!"DRAFT".equals(sale.getStatus())) {
            throw new BusinessRuleException(
                    "This sale is " + sale.getStatus() + " and cannot be confirmed from that status");
        }
        sale.setStatus("CONFIRMED");
        Sale saved = saleRepository.save(sale);

        writeLedgerEntry(saved);

        audit.recordForShaft("CONFIRM", "SALE", id, saved.getSaleNumber(), saved.getProjectId(), saved.getShaftId(),
                "Confirmed — " + saved.getNetAmount() + " " + saved.getCurrency());
        return toDetail(saved);
    }

    @Transactional
    public void cancel(Long id, String reason) {
        permissions.require("sales.approve");
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to cancel a sale");
        }
        Sale sale = load(id);
        requireScope(sale);
        if ("SETTLED".equals(sale.getSettlementStatus())) {
            throw new BusinessRuleException(
                    "This sale has already been consumed by settlement " + sale.getSettlementId()
                    + " and cannot be cancelled");
        }
        if ("CANCELLED".equals(sale.getStatus())) {
            throw new BusinessRuleException("This sale is already cancelled");
        }
        sale.setStatus("CANCELLED");
        saleRepository.save(sale);
        audit.recordForShaft("CANCEL", "SALE", id, sale.getSaleNumber(), sale.getProjectId(), sale.getShaftId(),
                "Cancelled — " + reason);
    }

    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("sales.delete");
        Sale sale = load(id);
        requireScope(sale);
        if (!"DRAFT".equals(sale.getStatus())) {
            throw new BusinessRuleException(
                    "This sale is " + sale.getStatus() + " and cannot be deleted — cancel a confirmed sale instead");
        }
        sale.softDelete(AuditContext.currentUser());
        saleRepository.save(sale);
        audit.record("DELETE", "SALE", id, sale.getSaleNumber(), "Deleted" + (reason == null ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- helpers

    private Sale load(Long id) {
        return saleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Sale", id));
    }

    private void requireScope(Sale sale) {
        if (sale.getShaftId() != null) {
            permissions.requireShaftAccess(sale.getShaftId(), sale.getProjectId(), AuditContext.currentRole());
        } else {
            permissions.requireProjectAccess(sale.getProjectId(), AuditContext.currentRole());
        }
    }

    private void requireEditable(Sale sale) {
        if (!"DRAFT".equals(sale.getStatus())) {
            throw new BusinessRuleException(
                    "This sale is " + sale.getStatus() + " and can no longer be edited"
                    + ("SETTLED".equals(sale.getSettlementStatus()) ? " (already settled)" : ""));
        }
    }

    private void validateUnit(String unitCode) {
        if (!productionUnitRepository.existsById(unitCode)) {
            throw new BusinessRuleException("Unknown production unit: " + unitCode);
        }
    }

    /** Frozen exchange rate: the base-currency figures never move once written. */
    private void computeAmounts(Sale sale) {
        BigDecimal deductions = nz(sale.getDeductionsAmount());
        BigDecimal tax = nz(sale.getTaxAmount());
        BigDecimal royalty = nz(sale.getRoyaltyAmount());

        BigDecimal gross = sale.getQuantity().multiply(sale.getUnitPrice()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(deductions).subtract(tax).subtract(royalty);

        sale.setGrossAmount(gross);
        sale.setNetAmount(net);
        sale.setGrossBaseAmount(gross.multiply(sale.getExchangeRate()).setScale(4, RoundingMode.HALF_UP));
        sale.setNetBaseAmount(net.multiply(sale.getExchangeRate()).setScale(4, RoundingMode.HALF_UP));
    }

    private void writeLedgerEntry(Sale sale) {
        LedgerEntry entry = new LedgerEntry();
        entry.setCompanyId(sale.getCompanyId());
        entry.setEntryDate(sale.getSaleDate());
        entry.setEntryType("REVENUE");
        entry.setDirection("CREDIT");
        entry.setProjectId(sale.getProjectId());
        entry.setMiningOperationId(sale.getMiningOperationId());
        entry.setShaftId(sale.getShaftId());
        entry.setDescription(sale.getSaleNumber() + " — " + sale.getProduct() + " " + sale.getQuantity() + " " + sale.getUnitCode());
        entry.setAmount(sale.getNetAmount());
        entry.setCurrency(sale.getCurrency());
        entry.setExchangeRate(sale.getExchangeRate());
        entry.setBaseAmount(sale.getNetBaseAmount());
        entry.setSourceTable("sales");
        entry.setSourceId(sale.getId());
        ledgerRepository.save(entry);
    }

    private void apply(Sale s, SaleRequest r) {
        s.setProjectId(r.projectId());
        s.setMiningOperationId(r.miningOperationId());
        s.setShaftId(r.shaftId());
        s.setContractId(r.contractId());
        s.setBatchId(r.batchId());
        s.setBuyerId(r.buyerId());
        s.setSaleDate(r.saleDate());
        s.setProduct(r.product() == null ? "GOLD" : r.product());
        s.setQuantity(r.quantity());
        s.setUnitCode(r.unitCode());
        s.setGrade(r.grade());
        s.setAssayReference(r.assayReference());
        s.setAssayPercent(r.assayPercent());
        s.setUnitPrice(r.unitPrice());
        s.setCurrency(r.currency());
        s.setExchangeRate(r.exchangeRate());
        s.setDeductionsAmount(nz(r.deductionsAmount()));
        s.setTaxAmount(nz(r.taxAmount()));
        s.setRoyaltyAmount(nz(r.royaltyAmount()));
        s.setInvoiceNumber(r.invoiceNumber());
        s.setReference(r.reference());
        s.setNotes(r.notes());
    }

    private SaleSummary toSummary(Sale s) {
        return new SaleSummary(
                s.getId(), s.getSaleNumber(), projectName(s.getProjectId()), shaftName(s.getShaftId()),
                buyerName(s.getBuyerId()), s.getSaleDate(), s.getProduct(), s.getQuantity(), s.getUnitCode(),
                s.getNetAmount(), s.getCurrency(), s.getStatus(), s.getSettlementStatus());
    }

    private SaleDetail toDetail(Sale s) {
        return new SaleDetail(
                s.getId(), s.getSaleNumber(), s.getProjectId(), projectName(s.getProjectId()),
                s.getMiningOperationId(), s.getShaftId(), shaftName(s.getShaftId()),
                s.getContractId(), s.getBatchId(), s.getBuyerId(), buyerName(s.getBuyerId()),
                s.getSaleDate(), s.getProduct(), s.getQuantity(), s.getUnitCode(), s.getGrade(),
                s.getAssayReference(), s.getAssayPercent(), s.getUnitPrice(), s.getCurrency(), s.getExchangeRate(),
                s.getGrossAmount(), s.getDeductionsAmount(), s.getTaxAmount(), s.getRoyaltyAmount(), s.getNetAmount(),
                s.getGrossBaseAmount(), s.getNetBaseAmount(), s.getPaymentStatus(), s.getAmountReceived(),
                s.getSettlementStatus(), s.getSettlementId(), s.getInvoiceNumber(), s.getReference(),
                s.getStatus(), s.getNotes(), s.getCreatedAt(), s.getCreatedBy());
    }

    private String projectName(Long id) {
        return id == null ? null : projectRepository.findById(id).map(Project::getName).orElse(null);
    }

    private String shaftName(Long id) {
        return id == null ? null : shaftRepository.findById(id).map(Shaft::getName).orElse(null);
    }

    private String buyerName(Long id) {
        return id == null ? null : buyerRepository.findById(id).map(Buyer::getName).orElse(null);
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private String nextSaleNumber() {
        String prefix = "SAL-" + LocalDate.now().getYear() + "-";
        long count = saleRepository.count() + 1;
        String candidate = prefix + String.format("%05d", count);
        while (saleRepository.existsBySaleNumberIgnoreCaseAndDeletedAtIsNull(candidate)) {
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
