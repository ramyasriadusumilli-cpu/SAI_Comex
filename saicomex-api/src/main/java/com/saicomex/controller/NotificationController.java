package com.saicomex.controller;

import com.saicomex.dto.AlertDtos.MarkAllReadResult;
import com.saicomex.dto.AlertDtos.NotificationDto;
import com.saicomex.dto.PageResponse;
import com.saicomex.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * SRS §46 — {@code /api/notifications}. Always the current user's own; there
 * is no cross-user read here, so no permission check beyond authentication.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final AlertService alertService;

    @GetMapping
    public PageResponse<NotificationDto> list(
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return alertService.myNotifications(pageable);
    }

    @GetMapping("/unread-count")
    public long unreadCount() {
        return alertService.unreadCount();
    }

    @PostMapping("/read-all")
    public MarkAllReadResult readAll() {
        return alertService.markAllRead();
    }
}
