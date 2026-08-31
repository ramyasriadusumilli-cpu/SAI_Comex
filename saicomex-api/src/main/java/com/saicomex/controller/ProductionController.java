package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.ProductionDtos.*;
import com.saicomex.service.ProductionService;
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

/** SRS §13, §14, §43 — {@code /api/production}. */
@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionService productionService;

    @GetMapping
    public PageResponse<ProductionSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25, sort = "productionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return productionService.list(status, projectId, shaftId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public ProductionDetail get(@PathVariable Long id) {
        return productionService.get(id);
    }

    /** Paged production history for one shaft. */
    @GetMapping("/shaft/{shaftId}")
    public PageResponse<ProductionSummary> historyForShaft(
            @PathVariable Long shaftId,
            @PageableDefault(size = 25, sort = "productionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return productionService.historyForShaft(shaftId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductionDetail create(@Valid @RequestBody ProductionRequest request) {
        return productionService.create(request);
    }

    @PutMapping("/{id}")
    public ProductionDetail update(@PathVariable Long id, @Valid @RequestBody ProductionRequest request) {
        return productionService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    public ProductionDetail submit(@PathVariable Long id) {
        return productionService.submit(id);
    }

    @PostMapping("/{id}/verify")
    public ProductionDetail verify(@PathVariable Long id, @RequestBody(required = false) VerifyRequest request) {
        return productionService.verify(id, request);
    }

    @PostMapping("/{id}/approve")
    public ProductionDetail approve(@PathVariable Long id) {
        return productionService.approve(id);
    }

    /** SRS §14 — a correction is a new record, not an edit of this one. */
    @PostMapping("/{id}/correct")
    public ProductionDetail correct(@PathVariable Long id, @Valid @RequestBody CorrectionRequest request) {
        return productionService.correct(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        productionService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
