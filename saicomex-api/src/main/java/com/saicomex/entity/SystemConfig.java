package com.saicomex.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SRS §41 — a typed configuration key/value so an administrator can change
 * behaviour without a redeploy.
 */
@Entity
@Table(name = "system_config")
@Getter
@Setter
public class SystemConfig {

    @Id
    @Column(name = "config_key", length = 80)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /** STRING | NUMBER | BOOLEAN | JSON */
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType = "STRING";

    @Column(nullable = false, length = 40)
    private String category = "GENERAL";

    @Column(length = 200)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_editable", nullable = false)
    private Boolean isEditable = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 160)
    private String updatedBy;
}
