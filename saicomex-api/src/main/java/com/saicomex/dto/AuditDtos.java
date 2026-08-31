package com.saicomex.dto;

import com.saicomex.entity.AuditLog;

import java.time.LocalDateTime;

/**
 * SRS §39. Response shape for the audit trail.
 */
public final class AuditDtos {

    private AuditDtos() {}

    public record AuditEntry(
            Long id,
            LocalDateTime occurredAt,
            String userEmail,
            String userRole,
            String action,
            String entityType,
            Long entityId,
            String entityLabel,
            Long projectId,
            Long shaftId,
            String fieldName,
            String oldValue,
            String newValue,
            String reason,
            String summary,
            String ipAddress
    ) {}

    public static AuditEntry toEntry(AuditLog a) {
        return new AuditEntry(
                a.getId(), a.getOccurredAt(), a.getUserEmail(), a.getUserRole(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getEntityLabel(), a.getProjectId(), a.getShaftId(),
                a.getFieldName(), a.getOldValue(), a.getNewValue(), a.getReason(), a.getSummary(), a.getIpAddress());
    }
}
