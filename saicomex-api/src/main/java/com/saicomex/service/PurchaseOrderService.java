package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.InventoryDtos.TransactionRequest;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.PurchaseOrderDtos.PoDetail;
import com.saicomex.dto.PurchaseOrderDtos.PoLineDetail;
import com.saicomex.dto.PurchaseOrderDtos.PoLineRequest;
import com.saicomex.dto.PurchaseOrderDtos.PoRequest;
import com.saicomex.dto.PurchaseOrderDtos.PoSummary;
import com.saicomex.dto.PurchaseOrderDtos.ReceiveLine;
import com.saicomex.dto.PurchaseOrderDtos.ReceiveRequest;
import com.saicomex.entity.Company;
import com.saicomex.entity.PurchaseOrder;
import com.saicomex.entity.PurchaseOrderLine;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.inventory.PurchaseOrderPolicy;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.PurchaseOrderLineRepository;
import com.saicomex.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SRS §19 — purchase orders and goods receipt. A receipt against an approved
 * order posts a stock RECEIPT for each received line (through
 * {@link InventoryService#postFor}, so the weighted-average cost is maintained
 * exactly as any other receipt) and advances the order to PARTIALLY_RECEIVED
 * or RECEIVED. Permissions reuse the {@code inventory.*} module — a purchase
 * order is inventory intake, and the storekeeper who receives goods holds it.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private static final List<String> EDITABLE = List.of("DRAFT");
    private static final List<String> RECEIVABLE = List.of("APPROVED", "PARTIALLY_RECEIVED");

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final InventoryService inventoryService;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<PoSummary> list(String status, Long supplierId, Long projectId,
                                        LocalDate from, LocalDate to, Pageable pageable) {
        permissions.require("inventory.view");
        Page<PurchaseOrder> page = poRepository.search(blank(status), supplierId, projectId, from, to, pageable);
        return PageResponse.of(page, PurchaseOrderService::toSummary);
    }

    @Transactional(readOnly = true)
    public PoDetail get(Long id) {
        permissions.require("inventory.view");
        return toDetail(po(id));
    }

    @Transactional
    public PoDetail create(PoRequest req) {
        permissions.require("inventory.create");

        PurchaseOrder po = new PurchaseOrder();
        po.setCompanyId(defaultCompanyId());
        po.setPoNumber(nextPoNumber());
        po.setStatus("DRAFT");
        applyHeader(po, req);
        PurchaseOrder saved = poRepository.save(po);

        BigDecimal subtotal = writeLines(saved.getId(), req.lines());
        saveTotals(saved, subtotal, req.taxAmount());

        audit.record("CREATE", "PURCHASE_ORDER", saved.getId(), saved.getPoNumber(),
                "PO " + saved.getPoNumber() + " created — " + saved.getTotalAmount() + " " + saved.getCurrency());
        return toDetail(saved);
    }

    @Transactional
    public PoDetail update(Long id, PoRequest req) {
        permissions.require("inventory.edit");
        PurchaseOrder po = po(id);
        if (!EDITABLE.contains(po.getStatus())) {
            throw new BusinessRuleException("A " + po.getStatus() + " purchase order can no longer be edited");
        }
        applyHeader(po, req);
        lineRepository.deleteAll(lineRepository.findByPurchaseOrderIdOrderByLineNo(id));
        BigDecimal subtotal = writeLines(id, req.lines());
        saveTotals(po, subtotal, req.taxAmount());
        return toDetail(po);
    }

    @Transactional
    public PoDetail submit(Long id) {
        permissions.require("inventory.edit");
        PurchaseOrder po = requireStatus(id, "DRAFT", "submitted");
        po.setStatus("SUBMITTED");
        poRepository.save(po);
        audit.record("SUBMIT", "PURCHASE_ORDER", id, po.getPoNumber(), "PO " + po.getPoNumber() + " submitted");
        return toDetail(po);
    }

    @Transactional
    public PoDetail approve(Long id) {
        permissions.require("inventory.edit");
        PurchaseOrder po = requireStatus(id, "SUBMITTED", "approved");
        po.setStatus("APPROVED");
        po.setApprovedBy(AuditContext.currentUser());
        po.setApprovedAt(LocalDateTime.now());
        poRepository.save(po);
        audit.record("APPROVE", "PURCHASE_ORDER", id, po.getPoNumber(), "PO " + po.getPoNumber() + " approved");
        return toDetail(po);
    }

    /**
     * Receive goods against an approved order. Each received line posts a stock
     * RECEIPT (if it names an inventory item) and advances its received quantity;
     * the order closes to RECEIVED once every line is fully in.
     */
    @Transactional
    public PoDetail receive(Long id, ReceiveRequest req) {
        permissions.require("inventory.create");
        PurchaseOrder po = po(id);
        if (!RECEIVABLE.contains(po.getStatus())) {
            throw new BusinessRuleException("A " + po.getStatus() + " purchase order cannot receive goods");
        }
        if (po.getStoreId() == null) {
            throw new BusinessRuleException("This purchase order has no store — set a store before receiving goods");
        }

        List<PurchaseOrderLine> lines = lineRepository.findByPurchaseOrderIdOrderByLineNo(id);
        for (ReceiveLine rl : req.lines()) {
            PurchaseOrderLine line = lines.stream().filter(l -> l.getId().equals(rl.lineId())).findFirst()
                    .orElseThrow(() -> new BusinessRuleException("Line " + rl.lineId() + " is not on this purchase order"));

            PurchaseOrderPolicy.validateReceive(line.getQuantity(), line.getReceivedQuantity(), rl.quantity());

            if (line.getItemId() != null) {
                inventoryService.postFor(new TransactionRequest(
                        line.getItemId(), po.getStoreId(), "RECEIPT", rl.quantity(), line.getUnitCost(), po.getCurrency(),
                        po.getProjectId(), null, po.getShaftId(), null, null, null, po.getSupplierId(), null, null,
                        "PO " + po.getPoNumber() + " goods receipt (line " + line.getLineNo() + ")",
                        po.getPoNumber(), LocalDateTime.now(), null));
            }
            line.setReceivedQuantity(line.getReceivedQuantity().add(rl.quantity()));
            lineRepository.save(line);
        }

        boolean allFull = lines.stream().allMatch(l -> l.getReceivedQuantity().compareTo(l.getQuantity()) >= 0);
        po.setStatus(PurchaseOrderPolicy.statusAfterReceipt(allFull, true));
        poRepository.save(po);

        audit.record("RECEIVE", "PURCHASE_ORDER", id, po.getPoNumber(),
                "Goods received against PO " + po.getPoNumber() + " — now " + po.getStatus());
        return toDetail(po);
    }

    @Transactional
    public PoDetail cancel(Long id, String reason) {
        permissions.require("inventory.edit");
        PurchaseOrder po = po(id);
        if ("RECEIVED".equals(po.getStatus()) || "CANCELLED".equals(po.getStatus())) {
            throw new BusinessRuleException("A " + po.getStatus() + " purchase order cannot be cancelled");
        }
        po.setStatus("CANCELLED");
        poRepository.save(po);
        audit.record("CANCEL", "PURCHASE_ORDER", id, po.getPoNumber(),
                "PO " + po.getPoNumber() + " cancelled" + (reason != null ? " — " + reason : ""));
        return toDetail(po);
    }

    // ---------------------------------------------------------------- helpers

    private void applyHeader(PurchaseOrder po, PoRequest req) {
        po.setSupplierId(req.supplierId());
        po.setProjectId(req.projectId());
        po.setShaftId(req.shaftId());
        po.setStoreId(req.storeId());
        po.setOrderDate(req.orderDate());
        po.setExpectedDate(req.expectedDate());
        po.setCurrency(req.currency());
        po.setNotes(req.notes());
    }

    private BigDecimal writeLines(Long poId, List<PoLineRequest> lineReqs) {
        BigDecimal subtotal = BigDecimal.ZERO;
        int no = 1;
        for (PoLineRequest lr : lineReqs) {
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setPurchaseOrderId(poId);
            line.setLineNo(no++);
            line.setItemId(lr.itemId());
            line.setDescription(lr.description());
            line.setQuantity(lr.quantity());
            line.setReceivedQuantity(BigDecimal.ZERO);
            line.setUnit(lr.unit());
            line.setUnitCost(lr.unitCost());
            BigDecimal total = PurchaseOrderPolicy.lineTotal(lr.quantity(), lr.unitCost());
            line.setLineTotal(total);
            lineRepository.save(line);
            subtotal = subtotal.add(total);
        }
        return subtotal;
    }

    private void saveTotals(PurchaseOrder po, BigDecimal subtotal, BigDecimal taxAmount) {
        BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        po.setSubtotal(subtotal);
        po.setTaxAmount(tax);
        po.setTotalAmount(subtotal.add(tax));
        poRepository.save(po);
    }

    private PurchaseOrder requireStatus(Long id, String expected, String action) {
        PurchaseOrder po = po(id);
        if (!expected.equals(po.getStatus())) {
            throw new BusinessRuleException(
                    "Only a " + expected + " purchase order can be " + action + " (this one is " + po.getStatus() + ")");
        }
        return po;
    }

    private PurchaseOrder po(Long id) {
        return poRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("PurchaseOrder", id));
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private String nextPoNumber() {
        String prefix = "PO-" + LocalDate.now().getYear() + "-";
        long count = poRepository.count() + 1;
        String candidate = prefix + String.format("%05d", count);
        while (poRepository.existsByPoNumberIgnoreCaseAndDeletedAtIsNull(candidate)) {
            count++;
            candidate = prefix + String.format("%05d", count);
        }
        return candidate;
    }

    private static String blank(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private static PoSummary toSummary(PurchaseOrder p) {
        return new PoSummary(p.getId(), p.getPoNumber(), p.getSupplierId(), p.getOrderDate(),
                p.getExpectedDate(), p.getCurrency(), p.getTotalAmount(), p.getStatus());
    }

    private PoDetail toDetail(PurchaseOrder p) {
        List<PoLineDetail> lines = lineRepository.findByPurchaseOrderIdOrderByLineNo(p.getId()).stream()
                .map(l -> new PoLineDetail(l.getId(), l.getLineNo(), l.getItemId(), l.getDescription(),
                        l.getQuantity(), l.getReceivedQuantity(), l.getUnit(), l.getUnitCost(), l.getLineTotal()))
                .toList();
        return new PoDetail(p.getId(), p.getPoNumber(), p.getSupplierId(), p.getProjectId(), p.getShaftId(),
                p.getStoreId(), p.getOrderDate(), p.getExpectedDate(), p.getCurrency(), p.getSubtotal(),
                p.getTaxAmount(), p.getTotalAmount(), p.getStatus(), p.getApprovedBy(), p.getApprovedAt(),
                p.getNotes(), lines);
    }
}
