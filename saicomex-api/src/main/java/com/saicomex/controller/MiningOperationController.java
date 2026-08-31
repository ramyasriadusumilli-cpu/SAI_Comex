package com.saicomex.controller;

import com.saicomex.dto.MiningOperationDtos.OperationDetail;
import com.saicomex.dto.MiningOperationDtos.OperationRequest;
import com.saicomex.dto.MiningOperationDtos.OperationSummary;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.MiningOperationService;
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
 * SRS §7 — {@code /api/operations}.
 */
@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class MiningOperationController {

    private final MiningOperationService operationService;

    @GetMapping
    public PageResponse<OperationSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return operationService.list(status, projectId, search, pageable);
    }

    /** Unpaged list for dropdowns and the hierarchy tree. */
    @GetMapping("/options")
    public List<OperationSummary> options(@RequestParam(required = false) Long projectId) {
        return operationService.options(projectId);
    }

    @GetMapping("/{id}")
    public OperationDetail get(@PathVariable Long id) {
        return operationService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OperationDetail create(@Valid @RequestBody OperationRequest request) {
        return operationService.create(request);
    }

    @PutMapping("/{id}")
    public OperationDetail update(@PathVariable Long id, @Valid @RequestBody OperationRequest request) {
        return operationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        operationService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
