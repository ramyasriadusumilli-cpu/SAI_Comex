package com.saicomex.dto;

import com.saicomex.entity.Alert;
import com.saicomex.entity.Notification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * SRS §31, §46. Response shapes for alerts and the notifications they fan out
 * to. There is no create request — alerts are raised by other services
 * through {@code AlertService.raise(...)}, never posted directly by a client.
 */
public final class AlertDtos {

    private AlertDtos() {}

    public record AlertSummary(
            Long id,
            String category,
            String severity,
            String title,
            String message,
            Long projectId,
            Long miningOperationId,
            Long shaftId,
            String entityType,
            Long entityId,
            BigDecimal actualValue,
            BigDecimal thresholdValue,
            String status,
            String acknowledgedBy,
            LocalDateTime acknowledgedAt,
            LocalDateTime resolvedAt,
            String resolutionNote,
            LocalDateTime triggeredAt
    ) {}

    /** Optional note attached to an acknowledge/resolve action. */
    public record AlertActionRequest(String note) {}

    /** Counts for the alerts dashboard tile. */
    public record AlertCounts(Map<String, Long> bySeverity, Map<String, Long> byStatus, long openTotal) {}

    public record NotificationDto(
            Long id,
            String category,
            String title,
            String message,
            String linkUrl,
            String severity,
            Boolean isRead,
            LocalDateTime readAt,
            LocalDateTime createdAt
    ) {}

    /** How many notifications were marked read by {@code POST /read-all}. */
    public record MarkAllReadResult(int count) {}

    public static AlertSummary toSummary(Alert a) {
        return new AlertSummary(
                a.getId(), a.getCategory(), a.getSeverity(), a.getTitle(), a.getMessage(),
                a.getProjectId(), a.getMiningOperationId(), a.getShaftId(),
                a.getEntityType(), a.getEntityId(), a.getActualValue(), a.getThresholdValue(),
                a.getStatus(), a.getAcknowledgedBy(), a.getAcknowledgedAt(),
                a.getResolvedAt(), a.getResolutionNote(), a.getTriggeredAt());
    }

    public static NotificationDto toDto(Notification n) {
        return new NotificationDto(
                n.getId(), n.getCategory(), n.getTitle(), n.getMessage(), n.getLinkUrl(),
                n.getSeverity(), n.getIsRead(), n.getReadAt(), n.getCreatedAt());
    }
}
