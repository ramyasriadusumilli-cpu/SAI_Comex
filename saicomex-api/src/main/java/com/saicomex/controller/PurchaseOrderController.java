package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.PurchaseOrderDtos.*;
import com.saicomex.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** SRS §19 — {@code /api/purchase-orders}: purchase orders and goods receipt. */
@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @GetMapping
    public PageResponse<PoSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, supplierId, projectId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public PoDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PoDetail create(@Valid @RequestBody PoRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PoDetail update(@PathVariable Long id, @Valid @RequestBody PoRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/submit")
    public PoDetail submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/approve")
    public PoDetail approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/receive")
    public PoDetail receive(@PathVariable Long id, @Valid @RequestBody ReceiveRequest request) {
        return service.receive(id, request);
    }

    @PostMapping("/{id}/cancel")
    public PoDetail cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return service.cancel(id, reason);
    }
}
