package com.saicomex.controller;

import com.saicomex.dto.MaintenanceDtos.*;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.MaintenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** SRS §22 — {@code /api/maintenance}: maintenance jobs and parts. */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService service;

    @GetMapping
    public PageResponse<MaintenanceSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, type, equipmentId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public MaintenanceDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceDetail create(@Valid @RequestBody MaintenanceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public MaintenanceDetail update(@PathVariable Long id, @Valid @RequestBody MaintenanceRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public MaintenanceDetail setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.setStatus(id, body.get("status"), body.get("reason"));
    }
}
