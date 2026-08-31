package com.saicomex.controller;

import com.saicomex.dto.FuelDtos.*;
import com.saicomex.service.FuelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** SRS §17 — {@code /api/fuel}: fuel purchases and issues. */
@RestController
@RequestMapping("/api/fuel")
@RequiredArgsConstructor
public class FuelController {

    private final FuelService fuelService;

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
