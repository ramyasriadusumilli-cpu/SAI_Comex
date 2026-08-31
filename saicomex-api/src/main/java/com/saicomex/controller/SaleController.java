package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.SaleDtos.SaleDetail;
import com.saicomex.dto.SaleDtos.SaleRequest;
import com.saicomex.dto.SaleDtos.SaleSummary;
import com.saicomex.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** SRS §23, §43 — {@code /api/sales}. */
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public PageResponse<SaleSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) Long buyerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25, sort = "saleDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return saleService.list(status, projectId, shaftId, buyerId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public SaleDetail get(@PathVariable Long id) {
        return saleService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleDetail create(@Valid @RequestBody SaleRequest request) {
        return saleService.create(request);
    }

    @PutMapping("/{id}")
    public SaleDetail update(@PathVariable Long id, @Valid @RequestBody SaleRequest request) {
        return saleService.update(id, request);
    }

    @PostMapping("/{id}/confirm")
    public SaleDetail confirm(@PathVariable Long id) {
        return saleService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @RequestParam String reason) {
        saleService.cancel(id, reason);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        saleService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
