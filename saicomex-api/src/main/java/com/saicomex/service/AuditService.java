package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.entity.AuditLog;
import com.saicomex.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * SRS §39 — the audit trail.
 *
 * <p>Writes run in {@code REQUIRES_NEW} so an audit row survives a rollback
 * of the business transaction. That is the point: an attempted change that
 * failed a constraint is exactly the kind of event an auditor wants to see,
 * and losing the trail whenever the operation errors would make the trail
 * a record only of successes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /** A create/delete/approve-style event with no field-level diff. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, Long entityId, String entityLabel, String summary) {
        write(action, entityType, entityId, entityLabel, null, null, null, summary, null, null);
    }

    /** A field-level change: old value, new value, and the stated reason. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordChange(String entityType, Long entityId, String entityLabel,
                             String field, Object oldValue, Object newValue, String reason) {
        if (Objects.equals(str(oldValue), str(newValue))) return;   // nothing changed
        write("UPDATE", entityType, entityId, entityLabel,
              field, str(oldValue), str(newValue),
              field + " changed", reason, null);
    }

    /** A change that belongs to a specific shaft — lets the shaft page show its own history. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordForShaft(String action, String entityType, Long entityId, String entityLabel,
                               Long projectId, Long shaftId, String summary) {
        AuditLog logRow = base(action, entityType, entityId, entityLabel);
        logRow.setProjectId(projectId);
        logRow.setShaftId(shaftId);
        logRow.setSummary(summary);
        save(logRow);
    }

    private void write(String action, String entityType, Long entityId, String entityLabel,
                       String field, String oldValue, String newValue,
                       String summary, String reason, Long shaftId) {
        AuditLog logRow = base(action, entityType, entityId, entityLabel);
        logRow.setFieldName(field);
        logRow.setOldValue(oldValue);
        logRow.setNewValue(newValue);
        logRow.setSummary(summary);
        logRow.setReason(reason);
        logRow.setShaftId(shaftId);
        save(logRow);
    }

    private AuditLog base(String action, String entityType, Long entityId, String entityLabel) {
        AuditLog logRow = new AuditLog();
        logRow.setOccurredAt(LocalDateTime.now());
        logRow.setUserEmail(AuditContext.currentUser());
        logRow.setUserRole(AuditContext.currentRole());
        logRow.setAction(action);
        logRow.setEntityType(entityType);
        logRow.setEntityId(entityId);
        logRow.setEntityLabel(entityLabel);

        HttpServletRequest req = currentRequest();
        if (req != null) {
            logRow.setIpAddress(clientIp(req));
            String ua = req.getHeader("User-Agent");
            if (ua != null) logRow.setUserAgent(ua.length() > 300 ? ua.substring(0, 300) : ua);
        }
        return logRow;
    }

    private void save(AuditLog row) {
        try {
            auditLogRepository.save(row);
        } catch (Exception e) {
            // An audit failure must never take down the operation the operator
            // was performing; it is logged loudly instead.
            log.error("AUDIT WRITE FAILED for {} {} — {}", row.getEntityType(), row.getEntityId(), e.getMessage());
        }
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes sra) ? sra.getRequest() : null;
    }

    private static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }
}
