package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SRS §36 — a platform user. Data scoping to projects/shafts is held
 * separately in {@link UserProjectAccess} and {@link UserShaftAccess}; an
 * empty assignment set means group-wide (unrestricted) visibility.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(length = 40)
    private String phone;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(length = 120)
    private String department;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    /** ACTIVE | SUSPENDED | PENDING | DISABLED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** SRS §38 — MFA. Secret is only populated once the user enrols. */
    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_secret", length = 120)
    private String mfaSecret;

    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 60)
    private String lastLoginIp;

    @Column(name = "reset_token", length = 120)
    private String resetToken;

    @Column(name = "reset_token_expires")
    private LocalDateTime resetTokenExpires;

    @Column(name = "preferred_currency", length = 3)
    private String preferredCurrency;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
}
