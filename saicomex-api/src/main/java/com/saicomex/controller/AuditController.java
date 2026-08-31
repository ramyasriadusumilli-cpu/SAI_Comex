package com.saicomex.controller;

import com.saicomex.dto.AuditDtos.AuditEntry;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.AuditQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * SRS §39 — {@code /api/audit}.
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    public PageResponse<AuditEntry> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditQueryService.list(action, entityType, userEmail, projectId, shaftId, from, to, pageable);
    }

    /** History panel for a single record. */
    @GetMapping("/entity/{entityType}/{entityId}")
    public PageResponse<AuditEntry> history(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return auditQueryService.history(entityType, entityId, pageable);
    }
}
