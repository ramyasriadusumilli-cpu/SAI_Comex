package com.saicomex.controller;

import com.saicomex.dto.AlertDtos.AlertActionRequest;
import com.saicomex.dto.AlertDtos.AlertCounts;
import com.saicomex.dto.AlertDtos.AlertSummary;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * SRS §31 — {@code /api/alerts}.
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public PageResponse<AlertSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long shaftId,
            @PageableDefault(size = 25, sort = "triggeredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return alertService.list(status, severity, category, projectId, shaftId, pageable);
    }

    @GetMapping("/summary")
    public AlertCounts summary() {
        return alertService.summary();
    }

    @PostMapping("/{id}/acknowledge")
    public AlertSummary acknowledge(@PathVariable Long id,
                                    @RequestBody(required = false) AlertActionRequest request) {
        return alertService.acknowledge(id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/resolve")
    public AlertSummary resolve(@PathVariable Long id,
                                @RequestBody(required = false) AlertActionRequest request) {
        return alertService.resolve(id, request == null ? null : request.note());
    }
}
