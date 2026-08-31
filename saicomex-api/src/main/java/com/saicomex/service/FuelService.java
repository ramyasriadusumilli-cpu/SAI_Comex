package com.saicomex.service;

import com.saicomex.dto.ExpenseDtos.ExpenseDetail;
import com.saicomex.dto.ExpenseDtos.ExpenseRequest;
import com.saicomex.dto.FuelDtos.FuelDetail;
import com.saicomex.dto.FuelDtos.FuelIssueRequest;
import com.saicomex.dto.FuelDtos.FuelPurchaseRequest;
import com.saicomex.dto.InventoryDtos.TransactionRequest;
import com.saicomex.entity.Company;
import com.saicomex.entity.FuelTransaction;
import com.saicomex.entity.InventoryItem;
import com.saicomex.entity.InventoryTransaction;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.FuelTransactionRepository;
import com.saicomex.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * SRS §17 — fuel movements. The defining behaviour is {@link #issue}: dispensing
 * fuel writes THREE rows in one transaction — the stock ISSUE (valued at the
 * running average), the DIRECT expense that lands the cost on the shaft, and the
 * fuel record that links them. Any failure rolls back all three, so a fuel issue
 * can never leave a stock movement without its cost, or vice versa.
 */
@Service
@RequiredArgsConstructor
public class FuelService {

    private final FuelTransactionRepository fuelRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryService inventoryService;
    private final ExpenseService expenseService;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Value("${app.reporting-currency:USD}")
    private String reportingCurrency;

    /** Receive fuel into a store: a stock RECEIPT plus a fuel PURCHASE record. */
    @Transactional
    public FuelDetail purchase(FuelPurchaseRequest req) {
        permissions.require("fuel.create");

        if (req.clientUuid() != null && !req.clientUuid().isBlank()) {
            var existing = fuelRepository.findByClientUuidAndDeletedAtIsNull(req.clientUuid());
            if (existing.isPresent()) return toDetail(existing.get());
        }

        LocalDateTime when = req.transactionDate() != null ? req.transactionDate() : LocalDateTime.now();

        InventoryTransaction inv = inventoryService.postFor(new TransactionRequest(
                req.itemId(), req.storeId(), "RECEIPT", req.quantityLitres(), req.unitCost(), req.currency(),
                null, null, null, null, null, null, req.supplierId(), null, null,
                "Fuel purchase" + (req.reference() != null ? " " + req.reference() : ""),
                req.reference(), when, sub(req.clientUuid(), "inv")));

        FuelTransaction fuel = new FuelTransaction();
        fuel.setCompanyId(defaultCompanyId());
        fuel.setInventoryTransactionId(inv.getId());
        fuel.setTransactionType("PURCHASE");
        fuel.setTransactionDate(when);
        fuel.setFuelType(req.fuelType());
        fuel.setItemId(req.itemId());
        fuel.setStoreId(req.storeId());
        fuel.setQuantityLitres(req.quantityLitres());
        fuel.setUnitCost(req.unitCost());
        fuel.setTotalCost(inv.getTotalCost());
        fuel.setCurrency(inv.getCurrency());
        fuel.setSupplierId(req.supplierId());
        fuel.setOpeningStock(inv.getBalanceAfter().subtract(inv.getQuantity()));
        fuel.setClosingStock(inv.getBalanceAfter());
        fuel.setReference(req.reference());
        fuel.setNotes(req.notes());
        fuel.setSource("WEB");
        fuel.setClientUuid(blank(req.clientUuid()));
        FuelTransaction saved = fuelRepository.save(fuel);

        audit.record("PURCHASE", "FUEL_TRANSACTION", saved.getId(), String.valueOf(saved.getId()),
                "Fuel purchase: " + req.quantityLitres() + "L " + req.fuelType());
        return toDetail(saved);
    }

    /**
     * Dispense fuel: stock ISSUE + auto DIRECT expense + fuel record, atomically.
     */
    @Transactional
    public FuelDetail issue(FuelIssueRequest req) {
        permissions.require("fuel.create");

        if (req.clientUuid() != null && !req.clientUuid().isBlank()) {
            var existing = fuelRepository.findByClientUuidAndDeletedAtIsNull(req.clientUuid());
            if (existing.isPresent()) return toDetail(existing.get());
        }

        InventoryItem item = item(req.itemId());
        LocalDateTime when = req.transactionDate() != null ? req.transactionDate() : LocalDateTime.now();
        String label = req.quantityLitres() + "L " + req.fuelType()
                + (req.equipmentId() != null ? " to equipment #" + req.equipmentId() : "");

        // 1) Stock ISSUE — valued by the inventory service at the running average.
        InventoryTransaction inv = inventoryService.postFor(new TransactionRequest(
                req.itemId(), req.storeId(), "ISSUE", req.quantityLitres(), null, req.currency(),
                req.projectId(), req.miningOperationId(), req.shaftId(), req.equipmentId(),
                req.recipientEmployeeId(), req.recipientName(), null, null, null,
                "Fuel issue: " + label, req.reference(), when, sub(req.clientUuid(), "inv")));

        BigDecimal cost = inv.getTotalCost();
        String currency = inv.getCurrency() != null ? inv.getCurrency()
                : (item.getCostCurrency() != null ? item.getCostCurrency() : reportingCurrency);
        BigDecimal rate = req.exchangeRate() != null ? req.exchangeRate() : BigDecimal.ONE;

        // 2) The cost lands on the shaft as a DIRECT expense (gate-free — the
        //    fuel.create permission already authorised this whole action).
        ExpenseDetail expense = expenseService.createFor(new ExpenseRequest(
                req.projectId(), req.miningOperationId(), req.shaftId(), req.expenseCategoryId(), null,
                when.toLocalDate(), "Fuel: " + label, req.quantityLitres(), "litre",
                unitCost(cost, req.quantityLitres()), cost, currency, rate, null,
                "DIRECT", false, req.reference(), null, null, req.notes(),
                sub(req.clientUuid(), "exp"), null));

        // 3) The fuel record, linking the two.
        FuelTransaction fuel = new FuelTransaction();
        fuel.setCompanyId(defaultCompanyId());
        fuel.setInventoryTransactionId(inv.getId());
        fuel.setExpenseId(expense.id());
        fuel.setTransactionType("ISSUE");
        fuel.setTransactionDate(when);
        fuel.setFuelType(req.fuelType());
        fuel.setItemId(req.itemId());
        fuel.setStoreId(req.storeId());
        fuel.setQuantityLitres(req.quantityLitres());
        fuel.setUnitCost(unitCost(cost, req.quantityLitres()));
        fuel.setTotalCost(cost);
        fuel.setCurrency(currency);
        fuel.setProjectId(req.projectId());
        fuel.setMiningOperationId(req.miningOperationId());
        fuel.setShaftId(req.shaftId());
        fuel.setEquipmentId(req.equipmentId());
        fuel.setRecipientEmployeeId(req.recipientEmployeeId());
        fuel.setRecipientName(req.recipientName());
        fuel.setOdometerReading(req.odometerReading());
        fuel.setHourMeterReading(req.hourMeterReading());
        fuel.setOpeningStock(inv.getBalanceAfter().subtract(inv.getQuantity()));
        fuel.setClosingStock(inv.getBalanceAfter());
        fuel.setReference(req.reference());
        fuel.setNotes(req.notes());
        fuel.setSource("WEB");
        fuel.setClientUuid(blank(req.clientUuid()));
        FuelTransaction saved = fuelRepository.save(fuel);

        audit.record("ISSUE", "FUEL_TRANSACTION", saved.getId(), String.valueOf(saved.getId()),
                "Fuel issue: " + label + " (stock txn " + inv.getTransactionNumber()
                        + ", expense " + expense.expenseNumber() + ")");
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public FuelDetail get(Long id) {
        permissions.require("fuel.view");
        return toDetail(fuelRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("FuelTransaction", id)));
    }

    // -------------------------------------------------------------- helpers

    private InventoryItem item(Long id) {
        return itemRepository.findById(id).orElseThrow(() -> NotFoundException.of("InventoryItem", id));
    }

    private static BigDecimal unitCost(BigDecimal total, BigDecimal litres) {
        if (total == null || litres == null || litres.signum() == 0) return null;
        return total.divide(litres, 6, RoundingMode.HALF_UP);
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private static String sub(String clientUuid, String suffix) {
        return (clientUuid == null || clientUuid.isBlank()) ? null : clientUuid + "-" + suffix;
    }

    private static String blank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static FuelDetail toDetail(FuelTransaction f) {
        return new FuelDetail(f.getId(), f.getTransactionType(), f.getTransactionDate(), f.getFuelType(),
                f.getItemId(), f.getStoreId(), f.getQuantityLitres(), f.getUnitCost(), f.getTotalCost(),
                f.getCurrency(), f.getProjectId(), f.getShaftId(), f.getEquipmentId(),
                f.getInventoryTransactionId(), f.getExpenseId(), f.getOdometerReading(), f.getHourMeterReading(),
                f.getOpeningStock(), f.getClosingStock(), f.getReference(), f.getCreatedBy());
    }
}
