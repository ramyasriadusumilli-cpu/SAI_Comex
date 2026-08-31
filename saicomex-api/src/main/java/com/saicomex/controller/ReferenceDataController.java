package com.saicomex.controller;

import com.saicomex.dto.ReferenceDtos.CurrencyDto;
import com.saicomex.dto.ReferenceDtos.ProductionUnitDto;
import com.saicomex.dto.ReferenceDtos.ReferenceData;
import com.saicomex.service.ReferenceDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code /api/reference} — lookup data for the SPA's dropdowns. Any
 * authenticated caller may read it; there is nothing here to protect.
 */
@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataService referenceDataService;

    /** One call that warms every dropdown the SPA needs at startup. */
    @GetMapping("/all")
    public ReferenceData all() {
        return referenceDataService.getAll();
    }

    @GetMapping("/currencies")
    public List<CurrencyDto> currencies() {
        return referenceDataService.currencies();
    }

    @GetMapping("/units")
    public List<ProductionUnitDto> units() {
        return referenceDataService.productionUnits();
    }
}
