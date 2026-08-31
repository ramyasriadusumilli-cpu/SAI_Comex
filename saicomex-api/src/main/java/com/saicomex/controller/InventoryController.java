package com.saicomex.controller;

import com.saicomex.dto.InventoryDtos.*;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** SRS §18, §19 — {@code /api/inventory}: items, stores, stock movements, balances. */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // -------------------------------------------------------------- items

    @GetMapping("/items")
    public PageResponse<ItemDetail> listItems(
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean controlled,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "name") Pageable pageable) {
        return inventoryService.listItems(itemType, active, controlled, search, pageable);
    }

    @GetMapping("/items/{id}")
    public ItemDetail getItem(@PathVariable Long id) {
        return inventoryService.getItem(id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDetail createItem(@Valid @RequestBody ItemRequest request) {
        return inventoryService.createItem(request);
    }

    @PutMapping("/items/{id}")
    public ItemDetail updateItem(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return inventoryService.updateItem(id, request);
    }

    // ------------------------------------------------------------- stores

    @GetMapping("/stores")
    public List<StoreDetail> listStores(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String storeType,
            @RequestParam(required = false) Long shaftId) {
        return inventoryService.listStores(active, storeType, shaftId);
    }

    @PostMapping("/stores")
    @ResponseStatus(HttpStatus.CREATED)
    public StoreDetail createStore(@Valid @RequestBody StoreRequest request) {
        return inventoryService.createStore(request);
    }

    @PutMapping("/stores/{id}")
    public StoreDetail updateStore(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        return inventoryService.updateStore(id, request);
    }

    // ------------------------------------------------------- transactions

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionDetail post(@Valid @RequestBody TransactionRequest request) {
        return inventoryService.post(request);
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionDetail> listTransactions(
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 25, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return inventoryService.listTransactions(itemId, storeId, shaftId, type, from, to, pageable);
    }

    // ----------------------------------------------------------- balances

    @GetMapping("/balances/store/{storeId}")
    public List<BalanceDetail> balancesForStore(@PathVariable Long storeId) {
        return inventoryService.balancesForStore(storeId);
    }

    @GetMapping("/balances/item/{itemId}")
    public List<BalanceDetail> balancesForItem(@PathVariable Long itemId) {
        return inventoryService.balancesForItem(itemId);
    }
}
