package com.saicomex.controller;

import com.saicomex.dto.FuelDtos.*;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.FuelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/** SRS §17 — {@code /api/fuel}: fuel purchases and issues. */
@RestController
@RequestMapping("/api/fuel")
@RequiredArgsConstructor
public class FuelController {

    private final FuelService fuelService;

    @GetMapping
    public PageResponse<FuelDetail> list(
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 25, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return fuelService.list(shaftId, fuelType, type, equipmentId, from, to, pageable);
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public FuelDetail purchase(@Valid @RequestBody FuelPurchaseRequest request) {
        return fuelService.purchase(request);
    }

    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public FuelDetail issue(@Valid @RequestBody FuelIssueRequest request) {
        return fuelService.issue(request);
    }

    @GetMapping("/{id}")
    public FuelDetail get(@PathVariable Long id) {
        return fuelService.get(id);
    }
}
