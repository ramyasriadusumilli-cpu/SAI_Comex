package com.saicomex.controller;

import com.saicomex.dto.ContractDtos.AmendmentRequest;
import com.saicomex.dto.ContractDtos.ContractDetail;
import com.saicomex.dto.ContractDtos.ContractRequest;
import com.saicomex.dto.ContractDtos.ContractSummary;
import com.saicomex.dto.ContractDtos.ContractVersionDto;
import com.saicomex.dto.ContractDtos.TerminateRequest;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SRS §10 — {@code /api/contracts}.
 */
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    public PageResponse<ContractSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Long contractTypeId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "effectiveDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return contractService.list(status, projectId, shaftId, partnerId, contractTypeId, search, pageable);
    }

    /** SRS §31 alert support — contracts expiring within {@code days}. */
    @GetMapping("/expiring")
    public List<ContractSummary> expiring(@RequestParam(defaultValue = "30") int days) {
        return contractService.expiring(days);
    }

    @GetMapping("/{id}")
    public ContractDetail get(@PathVariable Long id) {
        return contractService.get(id);
    }

    @GetMapping("/{id}/versions")
    public List<ContractVersionDto> versions(@PathVariable Long id) {
        return contractService.versions(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractDetail create(@Valid @RequestBody ContractRequest request) {
        return contractService.create(request);
    }

    @PutMapping("/{id}")
    public ContractDetail update(@PathVariable Long id, @Valid @RequestBody ContractRequest request) {
        return contractService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public ContractDetail activate(@PathVariable Long id) {
        return contractService.activate(id);
    }

    @PostMapping("/{id}/terminate")
    public ContractDetail terminate(@PathVariable Long id, @Valid @RequestBody TerminateRequest request) {
        return contractService.terminate(id, request.reason());
    }

    @PostMapping("/{id}/amend")
    public ContractDetail amend(@PathVariable Long id, @Valid @RequestBody AmendmentRequest request) {
        return contractService.amend(id, request);
    }
}
