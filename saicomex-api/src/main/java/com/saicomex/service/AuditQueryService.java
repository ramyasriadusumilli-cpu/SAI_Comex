package com.saicomex.service;

import com.saicomex.dto.AuditDtos;
import com.saicomex.dto.AuditDtos.AuditEntry;
import com.saicomex.dto.PageResponse;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * SRS §39 — read side of the audit trail. Writing is {@link AuditService}'s job.
 *
 * <p>Access is controlled by the {@code audit.view} permission and nothing
 * else. It is deliberately not behind a configuration flag: SRS §55 scenario
 * 11 requires that management can see who created, approved or modified every
 * critical transaction, and a deployment switch that silently turns that off
 * is the kind of thing nobody notices until an auditor asks.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final PermissionService permissions;

    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> list(String action, String entityType, String userEmail,
                                         Long projectId, Long shaftId, LocalDate from, LocalDate to,
                                         Pageable pageable) {
        permissions.require("audit.view");
        return PageResponse.of(auditLogRepository.search(
                blankToNull(action), blankToNull(entityType), blankToNull(userEmail),
                projectId, shaftId, startOfDay(from), endOfDay(to), pageable), AuditDtos::toEntry);
    }

    /** History panel on a single record. */
    @Transactional(readOnly = true)
    public PageResponse<AuditEntry> history(String entityType, Long entityId, Pageable pageable) {
        permissions.require("audit.view");
        return PageResponse.of(
                auditLogRepository.findAllByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId, pageable),
                AuditDtos::toEntry);
    }

    private static LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private static LocalDateTime endOfDay(LocalDate date) {
        return date == null ? null : date.atTime(LocalTime.MAX);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
