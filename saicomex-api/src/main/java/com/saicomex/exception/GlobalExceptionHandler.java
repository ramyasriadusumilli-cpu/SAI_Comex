package com.saicomex.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> businessRule(BusinessRuleException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        // The database constraint names carry the real meaning; translating the
        // common ones keeps operators out of the postgres manual.
        String root = ex.getMostSpecificCause().getMessage();
        String message = "This change conflicts with existing data";
        if (root != null) {
            if (root.contains("uq_contract_active_per_shaft")) {
                message = "That shaft already has an active contract. Supersede or terminate it first.";
            } else if (root.contains("uq_agreement_active_per_contract")) {
                message = "That contract already has an active commercial agreement.";
            } else if (root.contains("uq_settlement_period")) {
                message = "A settlement already exists for that shaft and period.";
            } else if (root.contains("ck_rule_percent_split")) {
                message = "The SAIComex and partner percentages must add up to exactly 100%.";
            } else if (root.contains("ck_agreement_default_split")) {
                message = "The default SAIComex and partner percentages must add up to exactly 100%.";
            } else if (root.contains("does not belong to project")) {
                message = root.substring(root.indexOf("Mining operation"));
            } else if (root.contains("duplicate key")) {
                message = "A record with that code or number already exists.";
            }
        }
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), root);
        return build(HttpStatus.CONFLICT, message, req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req, null);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message,
                                                      HttpServletRequest req, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", req.getRequestURI());
        if (fields != null && !fields.isEmpty()) body.put("fieldErrors", fields);
        return ResponseEntity.status(status).body(body);
    }
}
