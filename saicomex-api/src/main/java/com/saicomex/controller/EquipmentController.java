package com.saicomex.controller;

import com.saicomex.dto.EquipmentDtos.*;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** SRS §20-21 — {@code /api/equipment}: asset register and allocation history. */
@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService service;

    @GetMapping
    public PageResponse<EquipmentSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "assetNumber") Pageable pageable) {
        return service.list(status, type, shaftId, search, pageable);
    }

    @GetMapping("/{id}")
    public EquipmentDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EquipmentDetail create(@Valid @RequestBody EquipmentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public EquipmentDetail update(@PathVariable Long id, @Valid @RequestBody EquipmentRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/allocate")
    public EquipmentDetail allocate(@PathVariable Long id, @Valid @RequestBody AllocationRequest request) {
        return service.allocate(id, request);
    }

    @GetMapping("/{id}/allocations")
    public List<AllocationDetail> allocations(@PathVariable Long id) {
        return service.allocationHistory(id);
    }

    @PatchMapping("/{id}/status")
    public EquipmentDetail setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.setStatus(id, body.get("status"), body.get("reason"));
    }
}
