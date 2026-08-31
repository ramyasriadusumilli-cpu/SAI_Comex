package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.UserDtos.CreatedUser;
import com.saicomex.dto.UserDtos.PasswordResetRequest;
import com.saicomex.dto.UserDtos.UserDetail;
import com.saicomex.dto.UserDtos.UserRequest;
import com.saicomex.dto.UserDtos.UserStatusRequest;
import com.saicomex.dto.UserDtos.UserSummary;
import com.saicomex.entity.Company;
import com.saicomex.entity.Role;
import com.saicomex.entity.User;
import com.saicomex.entity.UserProjectAccess;
import com.saicomex.entity.UserProjectAccessId;
import com.saicomex.entity.UserShaftAccess;
import com.saicomex.entity.UserShaftAccessId;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * SRS §36 — platform user management: accounts, role assignment, and the
 * project/shaft scoping that {@link PermissionService} reads at request time.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "SUSPENDED", "PENDING", "DISABLED");
    private static final String DIRECTOR_ROLE_CODE = "DIRECTOR";
    private static final String INITIAL_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%";
    private static final int INITIAL_PASSWORD_LENGTH = 14;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserProjectAccessRepository projectAccessRepository;
    private final UserShaftAccessRepository shaftAccessRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;
    private final UserCacheService userCacheService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> list(String status, Long roleId, String search, Pageable pageable) {
        permissions.require("users.view");
        Page<User> page = userRepository.search(blankToNull(status), roleId, blankToNull(search), pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public UserDetail get(Long id) {
        permissions.require("users.view");
        return toDetail(load(id));
    }

    @Transactional
    public CreatedUser create(UserRequest req) {
        permissions.require("users.create");
        validateEmailUnique(req.email(), null);
        Role role = loadRole(req.roleId());
        String status = req.status() == null ? "ACTIVE" : req.status();
        if (!STATUSES.contains(status)) {
            throw new BusinessRuleException("Unknown user status: " + status);
        }

        String initialPassword = generateInitialPassword();

        User user = new User();
        user.setCompanyId(defaultCompanyId());
        apply(user, req, role.getId());
        user.setStatus(status);
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        user.setMustChangePassword(true);
        User saved = userRepository.save(user);

        replaceProjectAccess(saved.getId(), req.projectIds());
        replaceShaftAccess(saved.getId(), req.shaftIds());

        audit.record("CREATE", "USER", saved.getId(), saved.getEmail(),
                "User " + saved.getEmail() + " created with role " + role.getCode());
        return new CreatedUser(toDetail(saved), initialPassword);
    }

    @Transactional
    public UserDetail update(Long id, UserRequest req) {
        permissions.require("users.edit");
        User user = load(id);
        validateEmailUnique(req.email(), id);
        Role role = loadRole(req.roleId());
        String status = req.status() == null ? user.getStatus() : req.status();
        if (!STATUSES.contains(status)) {
            throw new BusinessRuleException("Unknown user status: " + status);
        }

        boolean deactivating = "ACTIVE".equals(user.getStatus()) && !"ACTIVE".equals(status);
        boolean demoting = !role.getId().equals(user.getRoleId());
        if (deactivating) {
            guardNotSelf(user, "deactivate");
        }
        if ((deactivating || demoting) && isLastActiveDirector(user)) {
            throw new BusinessRuleException(user.getEmail()
                    + " is the last remaining active user holding the DIRECTOR role — the system cannot be"
                    + " locked out of its own administration. Promote another user to DIRECTOR first.");
        }

        audit.recordChange("USER", id, user.getEmail(), "roleId", user.getRoleId(), role.getId(), null);
        audit.recordChange("USER", id, user.getEmail(), "status", user.getStatus(), status, null);

        apply(user, req, role.getId());
        user.setStatus(status);
        User saved = userRepository.save(user);

        replaceProjectAccess(id, req.projectIds());
        replaceShaftAccess(id, req.shaftIds());

        // The single most important line in this file: a status or role change
        // (or a delete, below) must evict the cached account state, or the
        // user's existing JWT keeps authenticating/authorising on stale data
        // until the cache entry naturally expires.
        userCacheService.evict(saved.getEmail());

        return toDetail(saved);
    }

    @Transactional
    public UserDetail updateStatus(Long id, UserStatusRequest req) {
        permissions.require("users.edit");
        User user = load(id);
        if (!STATUSES.contains(req.status())) {
            throw new BusinessRuleException("Unknown user status: " + req.status());
        }
        if (!"ACTIVE".equals(req.status())) {
            guardNotSelf(user, "deactivate");
            if (isLastActiveDirector(user)) {
                throw new BusinessRuleException(user.getEmail()
                        + " is the last remaining active user holding the DIRECTOR role and may not be deactivated —"
                        + " promote another user to DIRECTOR first.");
            }
        }

        audit.recordChange("USER", id, user.getEmail(), "status", user.getStatus(), req.status(), req.reason());
        user.setStatus(req.status());
        User saved = userRepository.save(user);

        // See the note on update(): status changes must evict the cache.
        userCacheService.evict(saved.getEmail());
        return toDetail(saved);
    }

    @Transactional
    public UserDetail resetPassword(Long id, PasswordResetRequest req) {
        permissions.require("users.edit");
        User user = load(id);
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        User saved = userRepository.save(user);

        audit.record("UPDATE", "USER", id, user.getEmail(), "Password reset by administrator");
        // Resetting also clears any lockout, which isActiveUser() takes into
        // account — evict so the unlock is visible on the user's very next request.
        userCacheService.evict(saved.getEmail());
        return toDetail(saved);
    }

    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("users.delete");
        User user = load(id);
        guardNotSelf(user, "delete");
        if (isLastActiveDirector(user)) {
            throw new BusinessRuleException(user.getEmail()
                    + " is the last remaining active user holding the DIRECTOR role and may not be deleted —"
                    + " promote another user to DIRECTOR first.");
        }

        user.softDelete(AuditContext.currentUser());
        user.setStatus("DISABLED");
        userRepository.save(user);
        audit.record("DELETE", "USER", id, user.getEmail(),
                "User account deleted" + (reason == null || reason.isBlank() ? "" : " — " + reason));

        // See the note on update(): deletion must evict the cache too.
        userCacheService.evict(user.getEmail());
    }

    // ---------------------------------------------------------------- helpers

    private User load(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> NotFoundException.of("User", id));
    }

    private Role loadRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> NotFoundException.of("Role", roleId));
    }

    private void validateEmailUnique(String email, Long existingId) {
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .filter(u -> !u.getId().equals(existingId))
                .ifPresent(u -> {
                    throw new BusinessRuleException("Email " + email + " is already in use by another account");
                });
    }

    private void guardNotSelf(User target, String action) {
        User me = permissions.currentUser();
        if (me.getId().equals(target.getId())) {
            throw new BusinessRuleException("You cannot " + action + " your own account");
        }
    }

    /**
     * True when {@code target} is the sole remaining ACTIVE holder of the
     * DIRECTOR role — the one thing this application refuses to let happen,
     * because it would lock the group out of its own administration.
     */
    private boolean isLastActiveDirector(User target) {
        if (!"ACTIVE".equals(target.getStatus())) return false;
        Role directorRole = roleRepository.findByCodeAndIsActiveTrue(DIRECTOR_ROLE_CODE).orElse(null);
        if (directorRole == null || !directorRole.getId().equals(target.getRoleId())) return false;
        long activeDirectors = userRepository
                .search("ACTIVE", directorRole.getId(), null, Pageable.unpaged())
                .getTotalElements();
        return activeDirectors <= 1;
    }

    private void apply(User u, UserRequest r, Long roleId) {
        u.setEmail(r.email());
        u.setFirstName(r.firstName());
        u.setLastName(r.lastName());
        u.setPhone(r.phone());
        u.setJobTitle(r.jobTitle());
        u.setDepartment(r.department());
        u.setRoleId(roleId);
        u.setPreferredCurrency(r.preferredCurrency());
    }

    private void replaceProjectAccess(Long userId, List<Long> projectIds) {
        projectAccessRepository.deleteAllByIdUserId(userId);
        List<Long> ids = projectIds == null ? List.of() : projectIds;
        ids.stream().distinct().forEach(projectId -> {
            UserProjectAccess access = new UserProjectAccess();
            access.setId(new UserProjectAccessId(userId, projectId));
            projectAccessRepository.save(access);
        });
    }

    private void replaceShaftAccess(Long userId, List<Long> shaftIds) {
        shaftAccessRepository.deleteAllByIdUserId(userId);
        List<Long> ids = shaftIds == null ? List.of() : shaftIds;
        ids.stream().distinct().forEach(shaftId -> {
            UserShaftAccess access = new UserShaftAccess();
            access.setId(new UserShaftAccessId(userId, shaftId));
            shaftAccessRepository.save(access);
        });
    }

    private UserSummary toSummary(User u) {
        Role role = roleRepository.findById(u.getRoleId()).orElse(null);
        return new UserSummary(
                u.getId(), u.getEmail(), fullName(u),
                role == null ? null : role.getCode(), role == null ? null : role.getName(),
                u.getDepartment(), u.getStatus(), u.getLastLoginAt(),
                projectAccessRepository.findAllByIdUserId(u.getId()).size(),
                shaftAccessRepository.findAllByIdUserId(u.getId()).size());
    }

    private UserDetail toDetail(User u) {
        Role role = roleRepository.findById(u.getRoleId()).orElse(null);
        return new UserDetail(
                u.getId(), u.getEmail(), fullName(u),
                role == null ? null : role.getCode(), role == null ? null : role.getName(),
                u.getDepartment(), u.getStatus(), u.getLastLoginAt(),
                projectAccessRepository.findAllByIdUserId(u.getId()).size(),
                shaftAccessRepository.findAllByIdUserId(u.getId()).size(),
                u.getJobTitle(), u.getPhone(), u.getPreferredCurrency(),
                u.getMfaEnabled(), u.getMustChangePassword(),
                projectAccessRepository.findProjectIdsByUserId(u.getId()),
                shaftAccessRepository.findShaftIdsByUserId(u.getId()),
                u.getCreatedAt(), u.getCreatedBy(), u.getUpdatedAt(), u.getUpdatedBy());
    }

    private static String fullName(User u) {
        return u.getFirstName() + " " + u.getLastName();
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream()
                .findFirst()
                .map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private static String generateInitialPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(INITIAL_PASSWORD_LENGTH);
        for (int i = 0; i < INITIAL_PASSWORD_LENGTH; i++) {
            sb.append(INITIAL_PASSWORD_CHARS.charAt(random.nextInt(INITIAL_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
