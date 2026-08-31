package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.InventoryDtos.BalanceDetail;
import com.saicomex.dto.InventoryDtos.ItemDetail;
import com.saicomex.dto.InventoryDtos.ItemRequest;
import com.saicomex.dto.InventoryDtos.StoreDetail;
import com.saicomex.dto.InventoryDtos.StoreRequest;
import com.saicomex.dto.InventoryDtos.TransactionDetail;
import com.saicomex.dto.InventoryDtos.TransactionRequest;
import com.saicomex.dto.PageResponse;
import com.saicomex.entity.Company;
import com.saicomex.entity.InventoryBalance;
import com.saicomex.entity.InventoryBalanceId;
import com.saicomex.entity.InventoryItem;
import com.saicomex.entity.InventoryTransaction;
import com.saicomex.entity.StoreLocation;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.inventory.ControlledItemPolicy;
import com.saicomex.inventory.InventoryValuation;
import com.saicomex.inventory.ValuationResult;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.InventoryBalanceRepository;
import com.saicomex.repository.InventoryItemRepository;
import com.saicomex.repository.InventoryTransactionRepository;
import com.saicomex.repository.StoreLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * SRS §18, §19 — inventory items, stores, and the stock movements that change
 * them. The service owns the invariant that {@code inventory_balances} is
 * updated in the same transaction as the movement that changes it: every
 * movement reads the current position, runs it through the pure
 * {@link InventoryValuation}, and writes back the new quantity, average cost
 * and the movement's own value together.
 */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Set<String> INBOUND = Set.of("RECEIPT", "TRANSFER_IN", "RETURN");
    private static final Set<String> OUTBOUND = Set.of("ISSUE", "TRANSFER_OUT");

    private final InventoryItemRepository itemRepository;
    private final StoreLocationRepository storeRepository;
    private final InventoryBalanceRepository balanceRepository;
    private final InventoryTransactionRepository txnRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    // ================================================================= items

    @Transactional(readOnly = true)
    public PageResponse<ItemDetail> listItems(String itemType, Boolean active, Boolean controlled,
                                              String search, Pageable pageable) {
        permissions.require("inventory.view");
        Page<InventoryItem> page = itemRepository.search(itemType, active, controlled, blank(search), pageable);
        return PageResponse.of(page, InventoryService::toItemDetail);
    }

    @Transactional(readOnly = true)
    public ItemDetail getItem(Long id) {
        permissions.require("inventory.view");
        return toItemDetail(item(id));
    }

    @Transactional
    public ItemDetail createItem(ItemRequest req) {
        permissions.require("inventory.create");
        if (itemRepository.existsByCodeIgnoreCase(req.code())) {
            throw new BusinessRuleException("An item with code " + req.code() + " already exists");
        }
        InventoryItem item = new InventoryItem();
        item.setCompanyId(defaultCompanyId());
        applyItem(item, req);
        InventoryItem saved = itemRepository.save(item);
        audit.record("CREATE", "INVENTORY_ITEM", saved.getId(), saved.getCode(),
                "Inventory item " + saved.getCode() + " created");
        return toItemDetail(saved);
    }

    @Transactional
    public ItemDetail updateItem(Long id, ItemRequest req) {
        permissions.require("inventory.edit");
        InventoryItem item = item(id);
        if (!item.getCode().equalsIgnoreCase(req.code()) && itemRepository.existsByCodeIgnoreCase(req.code())) {
            throw new BusinessRuleException("An item with code " + req.code() + " already exists");
        }
        applyItem(item, req);
        InventoryItem saved = itemRepository.save(item);
        audit.record("UPDATE", "INVENTORY_ITEM", saved.getId(), saved.getCode(),
                "Inventory item " + saved.getCode() + " updated");
        return toItemDetail(saved);
    }

    // ================================================================ stores

    @Transactional(readOnly = true)
    public List<StoreDetail> listStores(Boolean active, String storeType, Long shaftId) {
        permissions.require("inventory.view");
        return storeRepository.search(active, blank(storeType), shaftId).stream()
                .map(InventoryService::toStoreDetail).toList();
    }

    @Transactional
    public StoreDetail createStore(StoreRequest req) {
        permissions.require("inventory.create");
        if (storeRepository.existsByCodeIgnoreCase(req.code())) {
            throw new BusinessRuleException("A store with code " + req.code() + " already exists");
        }
        StoreLocation store = new StoreLocation();
        store.setCompanyId(defaultCompanyId());
        applyStore(store, req);
        StoreLocation saved = storeRepository.save(store);
        audit.record("CREATE", "STORE_LOCATION", saved.getId(), saved.getCode(),
                "Store " + saved.getCode() + " created");
        return toStoreDetail(saved);
    }

    @Transactional
    public StoreDetail updateStore(Long id, StoreRequest req) {
        permissions.require("inventory.edit");
        StoreLocation store = storeRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("StoreLocation", id));
        if (!store.getCode().equalsIgnoreCase(req.code()) && storeRepository.existsByCodeIgnoreCase(req.code())) {
            throw new BusinessRuleException("A store with code " + req.code() + " already exists");
        }
        applyStore(store, req);
        return toStoreDetail(storeRepository.save(store));
    }

    // ========================================================== transactions

    /** User-facing stock movement: gated on {@code inventory.create}. */
    @Transactional
    public TransactionDetail post(TransactionRequest req) {
        permissions.require("inventory.create");
        return toTxnDetail(doPost(req), item(req.itemId()), store(req.storeId()));
    }

    /**
     * Post a movement on behalf of another module (a fuel issue). The caller
     * authorises the compound action, so this does not re-gate on
     * {@code inventory.create}. Returns the persisted entity so the caller can
     * read its value and resulting balance.
     */
    @Transactional
    public InventoryTransaction postFor(TransactionRequest req) {
        return doPost(req);
    }

    private InventoryTransaction doPost(TransactionRequest req) {
        // Idempotent replay (Phase 5 offline sync).
        if (req.clientUuid() != null && !req.clientUuid().isBlank()) {
            var existing = txnRepository.findByClientUuidAndDeletedAtIsNull(req.clientUuid());
            if (existing.isPresent()) return existing.get();
        }

        InventoryItem item = item(req.itemId());
        StoreLocation store = store(req.storeId());
        int sign = signOf(req.transactionType());
        BigDecimal signedQty = req.quantity().abs().multiply(BigDecimal.valueOf(sign));

        // Data scoping, when the movement names a shaft/project.
        if (req.shaftId() != null && req.projectId() != null) {
            permissions.requireShaftAccess(req.shaftId(), req.projectId(), AuditContext.currentRole());
        } else if (req.projectId() != null) {
            permissions.requireProjectAccess(req.projectId(), AuditContext.currentRole());
        }

        // Controlled items (explosives) may only leave a magazine on a permit.
        if ("ISSUE".equals(req.transactionType())) {
            ControlledItemPolicy.validateIssue(
                    Boolean.TRUE.equals(item.getIsControlled()),
                    Boolean.TRUE.equals(item.getRequiresPermit()),
                    req.permitReference(), req.recipientName(), req.recipientEmployeeId(), store.getStoreType());
        }

        InventoryBalanceId balanceId = new InventoryBalanceId(item.getId(), store.getId());
        InventoryBalance balance = balanceRepository.findById(balanceId).orElseGet(() -> {
            InventoryBalance b = new InventoryBalance();
            b.setId(balanceId);
            b.setQuantity(BigDecimal.ZERO);
            b.setAverageCost(BigDecimal.ZERO);
            return b;
        });

        ValuationResult valued;
        try {
            valued = InventoryValuation.apply(
                    balance.getQuantity(), balance.getAverageCost(), signedQty, req.unitCost());
        } catch (IllegalStateException insufficient) {
            throw new BusinessRuleException("Insufficient stock of " + item.getCode()
                    + " in store " + store.getCode() + ": on hand " + balance.getQuantity()
                    + ", tried to issue " + req.quantity());
        }

        String currency = req.currency() != null ? req.currency() : item.getCostCurrency();

        balance.setQuantity(valued.newQuantity());
        balance.setAverageCost(valued.newAverageCost());
        balance.setCostCurrency(currency);
        balance.setLastMovementAt(LocalDateTime.now());
        balanceRepository.save(balance);

        InventoryTransaction txn = new InventoryTransaction();
        txn.setCompanyId(defaultCompanyId());
        txn.setTransactionNumber(nextTransactionNumber());
        txn.setItemId(item.getId());
        txn.setStoreId(store.getId());
        txn.setTransactionType(req.transactionType());
        txn.setTransactionDate(req.transactionDate() != null ? req.transactionDate() : LocalDateTime.now());
        txn.setQuantity(signedQty);
        txn.setUnitCost(req.unitCost());
        txn.setTotalCost(valued.movementValue());
        txn.setCurrency(currency);
        txn.setBalanceAfter(valued.newQuantity());
        txn.setProjectId(req.projectId());
        txn.setMiningOperationId(req.miningOperationId());
        txn.setShaftId(req.shaftId());
        txn.setEquipmentId(req.equipmentId());
        txn.setRecipientEmployeeId(req.recipientEmployeeId());
        txn.setRecipientName(req.recipientName());
        txn.setSupplierId(req.supplierId());
        txn.setTransferStoreId(req.transferStoreId());
        txn.setPermitReference(req.permitReference());
        txn.setReason(req.reason());
        txn.setReference(req.reference());
        txn.setSource("WEB");
        txn.setClientUuid(blank(req.clientUuid()));
        InventoryTransaction saved = txnRepository.save(txn);

        audit.record(req.transactionType(), "INVENTORY_TRANSACTION", saved.getId(), saved.getTransactionNumber(),
                saved.getTransactionNumber() + ": " + signedQty + " " + item.getUnit() + " of " + item.getCode()
                        + " @ " + store.getCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionDetail> listTransactions(Long itemId, Long storeId, Long shaftId,
                                                            String type, LocalDateTime from, LocalDateTime to,
                                                            Pageable pageable) {
        permissions.require("inventory.view");
        Page<InventoryTransaction> page = txnRepository.search(itemId, storeId, shaftId, blank(type), from, to, pageable);
        return PageResponse.of(page, t -> toTxnDetail(t,
                itemRepository.findById(t.getItemId()).orElse(null),
                storeRepository.findById(t.getStoreId()).orElse(null)));
    }

    // =============================================================== balances

    @Transactional(readOnly = true)
    public List<BalanceDetail> balancesForStore(Long storeId) {
        permissions.require("inventory.view");
        store(storeId);
        return balanceRepository.findByStore(storeId).stream().map(this::toBalanceDetail).toList();
    }

    @Transactional(readOnly = true)
    public List<BalanceDetail> balancesForItem(Long itemId) {
        permissions.require("inventory.view");
        item(itemId);
        return balanceRepository.findByItem(itemId).stream().map(this::toBalanceDetail).toList();
    }

    // ================================================================ helpers

    private int signOf(String type) {
        if (INBOUND.contains(type)) return 1;
        if (OUTBOUND.contains(type)) return -1;
        throw new BusinessRuleException("Unsupported movement type: " + type
                + " (supported: RECEIPT, ISSUE, RETURN, TRANSFER_IN, TRANSFER_OUT)");
    }

    private InventoryItem item(Long id) {
        return itemRepository.findById(id).orElseThrow(() -> NotFoundException.of("InventoryItem", id));
    }

    private StoreLocation store(Long id) {
        return storeRepository.findById(id).orElseThrow(() -> NotFoundException.of("StoreLocation", id));
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private String nextTransactionNumber() {
        String prefix = "INV-" + LocalDate.now().getYear() + "-";
        long count = txnRepository.count() + 1;
        String candidate = prefix + String.format("%05d", count);
        while (txnRepository.existsByTransactionNumberIgnoreCase(candidate)) {
            count++;
            candidate = prefix + String.format("%05d", count);
        }
        return candidate;
    }

    private void applyItem(InventoryItem item, ItemRequest req) {
        item.setCode(req.code());
        item.setName(req.name());
        item.setItemType(req.itemType());
        item.setCategoryId(req.categoryId());
        item.setUnit(req.unit());
        item.setIsControlled(Boolean.TRUE.equals(req.isControlled()));
        item.setRequiresPermit(Boolean.TRUE.equals(req.requiresPermit()));
        item.setMinimumStock(req.minimumStock());
        item.setMaximumStock(req.maximumStock());
        item.setReorderLevel(req.reorderLevel());
        item.setStandardCost(req.standardCost());
        item.setCostCurrency(req.costCurrency());
        item.setValuationMethod(req.valuationMethod() != null ? req.valuationMethod() : "WEIGHTED_AVG");
        item.setIsActive(req.isActive() == null || req.isActive());
        item.setNotes(req.notes());
    }

    private void applyStore(StoreLocation store, StoreRequest req) {
        store.setCode(req.code());
        store.setName(req.name());
        store.setProjectId(req.projectId());
        store.setShaftId(req.shaftId());
        store.setLocationId(req.locationId());
        store.setStoreType(req.storeType());
        store.setKeeperUserId(req.keeperUserId());
        store.setIsActive(req.isActive() == null || req.isActive());
    }

    private static String blank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static ItemDetail toItemDetail(InventoryItem i) {
        return new ItemDetail(i.getId(), i.getCode(), i.getName(), i.getItemType(), i.getCategoryId(), i.getUnit(),
                Boolean.TRUE.equals(i.getIsControlled()), Boolean.TRUE.equals(i.getRequiresPermit()),
                i.getMinimumStock(), i.getMaximumStock(), i.getReorderLevel(), i.getStandardCost(),
                i.getCostCurrency(), i.getValuationMethod(), Boolean.TRUE.equals(i.getIsActive()), i.getNotes());
    }

    private static StoreDetail toStoreDetail(StoreLocation s) {
        return new StoreDetail(s.getId(), s.getCode(), s.getName(), s.getProjectId(), s.getShaftId(),
                s.getLocationId(), s.getStoreType(), s.getKeeperUserId(), Boolean.TRUE.equals(s.getIsActive()));
    }

    private static TransactionDetail toTxnDetail(InventoryTransaction t, InventoryItem item, StoreLocation store) {
        return new TransactionDetail(t.getId(), t.getTransactionNumber(),
                t.getItemId(), item != null ? item.getCode() : null, item != null ? item.getName() : null,
                t.getStoreId(), store != null ? store.getName() : null,
                t.getTransactionType(), t.getTransactionDate(), t.getQuantity(), t.getUnitCost(), t.getTotalCost(),
                t.getCurrency(), t.getBalanceAfter(), t.getShaftId(), t.getExpenseId(), t.getPermitReference(),
                t.getRecipientName(), t.getReason(), t.getReference(), t.getCreatedBy());
    }

    private BalanceDetail toBalanceDetail(InventoryBalance b) {
        InventoryItem item = itemRepository.findById(b.getId().getItemId()).orElse(null);
        StoreLocation store = storeRepository.findById(b.getId().getStoreId()).orElse(null);
        return new BalanceDetail(b.getId().getItemId(), item != null ? item.getCode() : null,
                item != null ? item.getName() : null, item != null ? item.getUnit() : null,
                b.getId().getStoreId(), store != null ? store.getName() : null,
                b.getQuantity(), b.getAverageCost(), b.getCostCurrency(), b.getLastMovementAt());
    }
}
