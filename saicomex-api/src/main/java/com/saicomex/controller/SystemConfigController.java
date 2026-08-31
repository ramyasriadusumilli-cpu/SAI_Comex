package com.saicomex.controller;

import com.saicomex.entity.SystemConfig;
import com.saicomex.service.PermissionService;
import com.saicomex.service.SystemConfigService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SRS §41 — {@code /api/settings}.
 *
 * <p>{@link SystemConfigService} predates per-request permission checks and is
 * shared with startup/internal callers that have no authenticated caller to
 * check, so — deliberately, unlike every other controller in this
 * application — the permission check lives here instead of in the service.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;
    private final PermissionService permissions;

    @GetMapping
    public Map<String, List<ConfigItem>> all() {
        permissions.require("settings.view");
        Map<String, List<ConfigItem>> byCategory = new LinkedHashMap<>();
        for (SystemConfig c : configService.findAll()) {
            byCategory.computeIfAbsent(c.getCategory(), k -> new java.util.ArrayList<>()).add(ConfigItem.of(c));
        }
        return byCategory;
    }

    @GetMapping("/category/{category}")
    public List<ConfigItem> byCategory(@PathVariable String category) {
        permissions.require("settings.view");
        return configService.findByCategory(category).stream().map(ConfigItem::of).toList();
    }

    @PutMapping("/{key}")
    public ConfigItem update(@PathVariable String key, @RequestBody ConfigValueRequest request) {
        permissions.require("settings.edit");
        return ConfigItem.of(configService.update(key, request.value()));
    }

    public record ConfigValueRequest(@NotNull String value) {}

    public record ConfigItem(String key, String value, String valueType, String category,
                             String label, String description, boolean editable) {
        static ConfigItem of(SystemConfig c) {
            return new ConfigItem(c.getConfigKey(), c.getConfigValue(), c.getValueType(), c.getCategory(),
                    c.getLabel(), c.getDescription(), Boolean.TRUE.equals(c.getIsEditable()));
        }
    }
}
