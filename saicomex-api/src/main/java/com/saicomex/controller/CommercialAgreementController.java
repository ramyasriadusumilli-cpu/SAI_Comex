package com.saicomex.controller;

import com.saicomex.dto.AgreementDtos.AgreementDetail;
import com.saicomex.dto.AgreementDtos.AgreementRequest;
import com.saicomex.dto.AgreementDtos.AgreementSummary;
import com.saicomex.dto.AgreementDtos.RuleTypeOption;
import com.saicomex.service.CommercialAgreementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SRS §11 — {@code /api/agreements}.
 */
@RestController
@RequestMapping("/api/agreements")
@RequiredArgsConstructor
public class CommercialAgreementController {

    private final CommercialAgreementService agreementService;

    @GetMapping
    public List<AgreementSummary> listForContract(@RequestParam Long contractId) {
        return agreementService.listForContract(contractId);
    }

    /** The rule type catalogue, for the rule builder UI. */
    @GetMapping("/rule-types")
    public List<RuleTypeOption> ruleTypes() {
        return agreementService.ruleTypes();
    }

    @GetMapping("/{id}")
    public AgreementDetail get(@PathVariable Long id) {
        return agreementService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgreementDetail create(@Valid @RequestBody AgreementRequest request) {
        return agreementService.create(request);
    }

    @PutMapping("/{id}")
    public AgreementDetail update(@PathVariable Long id, @Valid @RequestBody AgreementRequest request) {
        return agreementService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public AgreementDetail activate(@PathVariable Long id) {
        return agreementService.activate(id);
    }
}
