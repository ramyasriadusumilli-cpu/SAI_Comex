package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.SupplierDtos.*;
import com.saicomex.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** SRS §19 — {@code /api/suppliers}. */
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;

    @GetMapping
    public PageResponse<SupplierDetail> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "name") Pageable pageable) {
        return service.list(status, type, search, pageable);
    }

    @GetMapping("/options")
    public List<SupplierOption> options() {
        return service.options();
    }

    @GetMapping("/{id}")
    public SupplierDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierDetail create(@Valid @RequestBody SupplierRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SupplierDetail update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request);
    }
}
