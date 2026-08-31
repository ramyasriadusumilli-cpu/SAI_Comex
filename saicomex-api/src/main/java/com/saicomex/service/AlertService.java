package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.AlertDtos;
import com.saicomex.dto.AlertDtos.AlertCounts;
import com.saicomex.dto.AlertDtos.AlertSummary;
import com.saicomex.dto.AlertDtos.MarkAllReadResult;
import com.saicomex.dto.AlertDtos.NotificationDto;
import com.saicomex.dto.PageResponse;
import com.saicomex.entity.Alert;
import com.saicomex.entity.AlertRule;
import com.saicomex.entity.Company;
import com.saicomex.entity.Notification;
import com.saicomex.entity.User;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.AlertRepository;
import com.saicomex.repository.AlertRuleRepository;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.NotificationRepository;
import com.saicomex.repository.RoleRepository;
import com.saicomex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SRS §31 (alerts) and §46 (notifications) — one service, because a
 * notification only ever exists as the fan-out of an alert being raised.
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final List<String> SEVERITIES = List.of("INFO", "WARNING", "CRITICAL");
    private static final List<String> STATUSES = List.of("OPEN", "ACKNOWLEDGED", "RESOLVED", "DISMISSED");

    private final AlertRepository alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    // ------------------------------------------------------------------ alerts

    @Transactional(readOnly = true)
    public PageResponse<AlertSummary> list(String status, String severity, String category,
                                           Long projectId, Long shaftId, Pageable pageable) {
        permissions.require("alerts.view");
        Page<Alert> page = alertRepository.search(
                blankToNull(status), blankToNull(severity), blankToNull(category), projectId, shaftId, pageable);
        return PageResponse.of(page, AlertDtos::toSummary);
    }

    @Transactional(readOnly = true)
    public AlertCounts summary() {
        permissions.require("alerts.view");
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (String s : SEVERITIES) {
            bySeverity.put(s, alertRepository.search(null, s, null, null, null, Pageable.unpaged()).getTotalElements());
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : STATUSES) {
            byStatus.put(s, alertRepository.countByStatus(s));
        }
        return new AlertCounts(bySeverity, byStatus, alertRepository.countByStatus("OPEN"));
    }

    @Transactional
    public AlertSummary acknowledge(Long id, String note) {
        permissions.require("alerts.acknowledge");
        Alert alert = loadAlert(id);
        if (!"OPEN".equals(alert.getStatus())) {
            throw new BusinessRuleException("Only an OPEN alert can be acknowledged (current status: " + alert.getStatus() + ")");
        }
        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedBy(AuditContext.currentUser());
        alert.setAcknowledgedAt(LocalDateTime.now());
        Alert saved = alertRepository.save(alert);
        audit.record("APPROVE", "ALERT", id, alert.getTitle(),
                "Alert acknowledged" + (note == null || note.isBlank() ? "" : " — " + note));
        return AlertDtos.toSummary(saved);
    }

    @Transactional
    public AlertSummary resolve(Long id, String note) {
        permissions.require("alerts.resolve");
        Alert alert = loadAlert(id);
        if ("RESOLVED".equals(alert.getStatus())) {
            throw new BusinessRuleException("Alert is already resolved");
        }
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolutionNote(note);
        Alert saved = alertRepository.save(alert);
        audit.record("UPDATE", "ALERT", id, alert.getTitle(),
                "Alert resolved" + (note == null || note.isBlank() ? "" : " — " + note));
        return AlertDtos.toSummary(saved);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return alertRepository.countByStatus("OPEN");
    }

    /**
     * Raises an alert for other services to call — never exposed on a
     * controller, so it carries no permission check of its own; the caller's
     * own action has already been authorised.
     *
     * <p>Respects the dedupe key: an OPEN alert already sharing it means the
     * condition is already flagged, so nothing new is raised. On a genuine
     * raise, one {@link Notification} is fanned out to every ACTIVE user whose
     * role code appears in the rule's {@code notifyRoles} CSV.
     */
    @Transactional
    public Optional<Alert> raise(String category, String severity, String title, String message,
                                 Long projectId, Long miningOperationId, Long shaftId,
                                 String entityType, Long entityId,
                                 BigDecimal actualValue, BigDecimal thresholdValue,
                                 String dedupeKey, Long alertRuleId) {
        if (dedupeKey != null && alertRepository.existsByDedupeKeyAndStatus(dedupeKey, "OPEN")) {
            return Optional.empty();
        }

        Alert alert = new Alert();
        alert.setCompanyId(defaultCompanyId());
        alert.setAlertRuleId(alertRuleId);
        alert.setCategory(category);
        alert.setSeverity(severity == null ? "WARNING" : severity);
        alert.setTitle(title);
        alert.setMessage(message);
        alert.setProjectId(projectId);
        alert.setMiningOperationId(miningOperationId);
        alert.setShaftId(shaftId);
        alert.setEntityType(entityType);
        alert.setEntityId(entityId);
        alert.setActualValue(actualValue);
        alert.setThresholdValue(thresholdValue);
        alert.setDedupeKey(dedupeKey);
        Alert saved = alertRepository.save(alert);

        audit.record("CREATE", "ALERT", saved.getId(), saved.getTitle(), "Alert raised: " + saved.getMessage());

        if (alertRuleId != null) {
            alertRuleRepository.findById(alertRuleId).ifPresent(rule -> notifyRoles(saved, rule));
        }
        return Optional.of(saved);
    }

    private void notifyRoles(Alert alert, AlertRule rule) {
        String csv = rule.getNotifyRoles();
        if (csv == null || csv.isBlank()) return;
        for (String roleCode : csv.split(",")) {
            String code = roleCode.trim();
            if (code.isEmpty()) continue;
            roleRepository.findByCodeAndIsActiveTrue(code).ifPresent(role -> {
                List<User> recipients = userRepository.search("ACTIVE", role.getId(), null, Pageable.unpaged()).getContent();
                for (User user : recipients) {
                    Notification n = new Notification();
                    n.setUserId(user.getId());
                    n.setAlertId(alert.getId());
                    n.setCategory(alert.getCategory());
                    n.setTitle(alert.getTitle());
                    n.setMessage(alert.getMessage());
                    n.setSeverity(alert.getSeverity());
                    notificationRepository.save(n);
                }
            });
        }
    }

    // ------------------------------------------------------------- notifications

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> myNotifications(Pageable pageable) {
        User me = permissions.currentUser();
        return PageResponse.of(notificationRepository.findAllByUserIdOrderByCreatedAtDesc(me.getId(), pageable),
                AlertDtos::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        User me = permissions.currentUser();
        return notificationRepository.countByUserIdAndIsReadFalse(me.getId());
    }

    @Transactional
    public MarkAllReadResult markAllRead() {
        User me = permissions.currentUser();
        int count = notificationRepository.markAllReadForUser(me.getId(), LocalDateTime.now());
        return new MarkAllReadResult(count);
    }

    // ---------------------------------------------------------------- helpers

    private Alert loadAlert(Long id) {
        return alertRepository.findById(id).orElseThrow(() -> NotFoundException.of("Alert", id));
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream()
                .findFirst()
                .map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
