package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.PartnerDtos.PartnerDetail;
import com.saicomex.dto.PartnerDtos.PartnerRequest;
import com.saicomex.dto.PartnerDtos.PartnerSummary;
import com.saicomex.service.PartnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SRS §9 — {@code /api/partners}.
 *
 * <p>Banking-field redaction happens in the service, not here — this
 * controller never sees an unredacted {@code PartnerDetail} to accidentally
 * pass through.
 */
@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @GetMapping
    public PageResponse<PartnerSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "legalName", direction = Sort.Direction.ASC) Pageable pageable) {
        return partnerService.list(status, search, pageable);
    }

    /** Unpaged list for dropdowns. */
    @GetMapping("/options")
    public List<PartnerSummary> options() {
        return partnerService.listAll();
    }

    @GetMapping("/{id}")
    public PartnerDetail get(@PathVariable Long id) {
        return partnerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartnerDetail create(@Valid @RequestBody PartnerRequest request) {
        return partnerService.create(request);
    }

    @PutMapping("/{id}")
    public PartnerDetail update(@PathVariable Long id, @Valid @RequestBody PartnerRequest request) {
        return partnerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        partnerService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
