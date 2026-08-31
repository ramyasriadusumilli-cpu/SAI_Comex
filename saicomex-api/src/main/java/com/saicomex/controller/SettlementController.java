package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.SettlementDtos.*;
import com.saicomex.service.SettlementService;
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

/** SRS §25, §43 — {@code /api/settlements}. */
@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public PageResponse<SettlementSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25, sort = "periodEnd", direction = Sort.Direction.DESC) Pageable pageable) {
        return settlementService.list(status, projectId, shaftId, partnerId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public SettlementDetail get(@PathVariable Long id) {
        return settlementService.get(id);
    }

    /** Dry run — computes the full waterfall and writes nothing. */
    @PostMapping("/preview")
    public PreviewResult preview(@Valid @RequestBody SettlementRequest request) {
        return settlementService.preview(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementDetail calculate(@Valid @RequestBody SettlementRequest request) {
        return settlementService.calculate(request);
    }

    @PostMapping("/{id}/recalculate")
    public SettlementDetail recalculate(@PathVariable Long id) {
        return settlementService.recalculate(id);
    }

    @PostMapping("/{id}/approve")
    public SettlementDetail approve(@PathVariable Long id, @RequestBody(required = false) ApprovalRequest request) {
        return settlementService.approve(id, request);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id, @RequestParam String reason) {
        settlementService.cancel(id, reason);
        return ResponseEntity.noContent().build();
    }

    /** SRS §25 — the partner's position across every shaft they participate in. */
    @GetMapping("/partner/{partnerId}/statement")
    public PartnerStatement partnerStatement(@PathVariable Long partnerId) {
        return settlementService.partnerStatement(partnerId);
    }
}
