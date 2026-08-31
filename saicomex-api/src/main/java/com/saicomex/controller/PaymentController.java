package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.PaymentDtos.PaymentDetail;
import com.saicomex.dto.PaymentDtos.PaymentRequest;
import com.saicomex.dto.PaymentDtos.PaymentSummary;
import com.saicomex.service.PaymentService;
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

/** SRS §27, §43 — {@code /api/payments}. */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public PageResponse<PaymentSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long shaftId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 25, sort = "paymentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.list(status, paymentType, partnerId, projectId, shaftId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public PaymentDetail get(@PathVariable Long id) {
        return paymentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentDetail create(@Valid @RequestBody PaymentRequest request) {
        return paymentService.create(request);
    }

    @PutMapping("/{id}")
    public PaymentDetail update(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
        return paymentService.update(id, request);
    }

    @PostMapping("/{id}/approve")
    public PaymentDetail approve(@PathVariable Long id) {
        return paymentService.approve(id);
    }

    @PostMapping("/{id}/mark-paid")
    public PaymentDetail markPaid(@PathVariable Long id) {
        return paymentService.markPaid(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        paymentService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
