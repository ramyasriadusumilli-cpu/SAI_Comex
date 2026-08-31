package com.saicomex.controller;

import com.saicomex.dto.ExpenseDtos.*;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** SRS §15, §16, §43 — {@code /api/expenses}. */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public PageResponse<ExpenseSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return expenseService.list(status, projectId, shaftId, categoryId, from, to, search, pageable);
    }

    @GetMapping("/{id}")
    public ExpenseDetail get(@PathVariable Long id) {
        return expenseService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDetail create(@Valid @RequestBody ExpenseRequest request) {
        return expenseService.create(request);
    }

    @PutMapping("/{id}")
    public ExpenseDetail update(@PathVariable Long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    public ExpenseDetail submit(@PathVariable Long id) {
        return expenseService.submit(id);
    }

    @PostMapping("/{id}/approve")
    public ExpenseDetail approve(@PathVariable Long id, @RequestBody(required = false) ApprovalDecision request) {
        return expenseService.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public ExpenseDetail reject(@PathVariable Long id, @Valid @RequestBody RejectionRequest request) {
        return expenseService.reject(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        expenseService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
