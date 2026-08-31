package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.entity.SystemConfig;
import com.saicomex.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SRS §41 — the configuration engine. Behaviour that the business may want to
 * change (alert thresholds, approval settings, rounding) is read from here at
 * the point of use, never baked into a constant.
 */
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository repository;
    private final AuditService auditService;

    @Cacheable(value = "systemConfig", key = "#key")
    @Transactional(readOnly = true)
    public String getString(String key, String defaultValue) {
        return repository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        try {
            return new BigDecimal(getString(key, defaultValue.toPlainString()).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getString(key, String.valueOf(defaultValue)).trim());
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> findByCategory(String category) {
        return repository.findAllByCategoryOrderByConfigKeyAsc(category);
    }

    @CacheEvict(value = "systemConfig", key = "#key")
    @Transactional
    public SystemConfig update(String key, String value) {
        SystemConfig config = repository.findByConfigKey(key)
                .orElseThrow(() -> new com.saicomex.exception.NotFoundException("Configuration key " + key + " does not exist"));

        if (Boolean.FALSE.equals(config.getIsEditable())) {
            throw new com.saicomex.exception.BusinessRuleException(
                    "Configuration key " + key + " is not editable through the application");
        }

        String previous = config.getConfigValue();
        config.setConfigValue(value);
        config.setUpdatedAt(LocalDateTime.now());
        config.setUpdatedBy(AuditContext.currentUser());
        SystemConfig saved = repository.save(config);

        auditService.recordChange("SYSTEM_CONFIG", null, key, "configValue", previous, value, null);
        return saved;
    }

    @CacheEvict(value = "systemConfig", allEntries = true)
    public void evictAll() {
        // Used after a bulk configuration import.
    }
}
