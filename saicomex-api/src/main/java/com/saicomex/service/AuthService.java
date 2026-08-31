package com.saicomex.service;

import com.saicomex.dto.AuthDtos.*;
import com.saicomex.entity.Company;
import com.saicomex.entity.Role;
import com.saicomex.entity.User;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.repository.*;
import com.saicomex.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * SRS §38 — sign-in, sign-out, password lifecycle.
 *
 * <p>Failure handling is deliberately uniform: a wrong password, an unknown
 * email and a disabled account all produce the same message and the same
 * timing-insensitive path, so the endpoint cannot be used to enumerate who
 * has an account. The detail goes to the log and the audit trail, where it
 * belongs, not to the caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String GENERIC_FAILURE = "Incorrect email or password";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CompanyRepository companyRepository;
    private final UserProjectAccessRepository projectAccessRepository;
    private final UserShaftAccessRepository shaftAccessRepository;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserCacheService userCache;
    private final TokenBlacklistService blacklist;
    private final SystemConfigService config;
    private final AuditService audit;

    @Value("${app.jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${app.reporting-currency:USD}")
    private String reportingCurrency;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> {
                    audit.record("LOGIN_FAILED", "USER", null, request.email(), "No such account");
                    return new BadCredentialsException(GENERIC_FAILURE);
                });

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            audit.record("LOGIN_BLOCKED", "USER", user.getId(), user.getEmail(), "Account temporarily locked");
            throw new BadCredentialsException(
                    "This account is temporarily locked after repeated failed sign-ins. Try again later.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(user);
            throw new BadCredentialsException(GENERIC_FAILURE);
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            audit.record("LOGIN_BLOCKED", "USER", user.getId(), user.getEmail(),
                    "Account status is " + user.getStatus());
            throw new BadCredentialsException(
                    "This account is not active. Contact your administrator.");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ipAddress);
        userRepository.save(user);
        userCache.evict(user.getEmail());

        Role role = role(user);
        List<String> permissions = permissionService.permissionCodesForRole(user.getRoleId());
        String token = jwtUtil.generateToken(user.getEmail(), role.getCode(), permissions, user.getId());

        audit.record("LOGIN", "USER", user.getId(), user.getEmail(), "Signed in as " + role.getCode());

        return new LoginResponse(
                token, expirationMs, user.getId(), user.getEmail(), fullName(user),
                role.getCode(), role.getName(), permissions,
                projectAccessRepository.findProjectIdsByUserId(user.getId()),
                shaftAccessRepository.findShaftIdsByUserId(user.getId()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getPreferredCurrency());
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser() {
        User user = permissionService.currentUser();
        Role role = role(user);
        String companyName = companyRepository.findAll().stream()
                .findFirst().map(Company::getName).orElse("SAIComex Mining Company");

        return new CurrentUser(
                user.getId(), user.getEmail(), fullName(user),
                role.getCode(), role.getName(),
                permissionService.permissionCodesForRole(user.getRoleId()),
                projectAccessRepository.findProjectIdsByUserId(user.getId()),
                shaftAccessRepository.findShaftIdsByUserId(user.getId()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getPreferredCurrency(),
                companyName, reportingCurrency);
    }

    /**
     * Sign-out. The token's jti is blacklisted until its own expiry — without
     * that, "sign out" on a stateless JWT is a client-side illusion and a
     * copied token keeps working for the rest of the day.
     */
    @Transactional
    public void logout(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) return;
        String token = bearerToken.substring(7);
        if (!jwtUtil.isValid(token)) return;
        blacklist.revoke(jwtUtil.extractJti(token), jwtUtil.extractExpiry(token));
        audit.record("LOGOUT", "USER", jwtUtil.extractUserId(token), jwtUtil.extractEmail(token), "Signed out");
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = permissionService.currentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Your current password is not correct");
        }
        enforcePasswordPolicy(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("The new password must be different from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
        userCache.evict(user.getEmail());

        audit.record("PASSWORD_CHANGE", "USER", user.getId(), user.getEmail(), "Password changed by the account holder");
    }

    /**
     * Issues a reset token. Always reports success, whether or not the address
     * is known — the response must not reveal which addresses have accounts.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email()).ifPresent(user -> {
            String token = randomToken();
            user.setResetToken(token);
            user.setResetTokenExpires(LocalDateTime.now().plusHours(2));
            userRepository.save(user);
            audit.record("PASSWORD_RESET_REQUESTED", "USER", user.getId(), user.getEmail(), null);
            // The mail send is intentionally not awaited here; delivery failure
            // must not turn into a 500 that tells the caller the address exists.
            log.info("Password reset token issued for {}", user.getEmail());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.token())
                .filter(u -> u.getDeletedAt() == null)
                .filter(u -> u.getResetTokenExpires() != null
                          && u.getResetTokenExpires().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessRuleException(
                        "This reset link is invalid or has expired. Request a new one."));

        enforcePasswordPolicy(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setResetToken(null);
        user.setResetTokenExpires(null);
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        userCache.evict(user.getEmail());

        audit.record("PASSWORD_RESET", "USER", user.getId(), user.getEmail(), "Password reset via emailed token");
    }

    // ---------------------------------------------------------------- helpers

    private void registerFailure(User user) {
        int max = config.getInt("security.max_failed_logins", 5);
        int failures = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        user.setFailedLoginCount(failures);
        if (failures >= max) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
            audit.record("ACCOUNT_LOCKED", "USER", user.getId(), user.getEmail(),
                    failures + " consecutive failed sign-ins");
        }
        userRepository.save(user);
        userCache.evict(user.getEmail());
    }

    private void enforcePasswordPolicy(String password) {
        int min = config.getInt("security.password_min_length", 10);
        if (password == null || password.length() < min) {
            throw new BusinessRuleException("Password must be at least " + min + " characters");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessRuleException("Password must contain both letters and numbers");
        }
    }

    private Role role(User user) {
        return roleRepository.findById(user.getRoleId())
                .orElseThrow(() -> new BusinessRuleException(
                        "Your account is assigned a role that no longer exists. Contact your administrator."));
    }

    private static String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }

    static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
