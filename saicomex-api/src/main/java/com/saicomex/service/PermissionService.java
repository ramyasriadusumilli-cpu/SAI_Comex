package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.entity.User;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.PermissionRepository;
import com.saicomex.repository.UserProjectAccessRepository;
import com.saicomex.repository.UserShaftAccessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SRS §37 — the authoritative permission check, and the data-scoping rules
 * that sit on top of it.
 *
 * <p>Two layers, and the distinction matters:
 * <ul>
 *   <li><b>Permission</b> — may this role perform this action at all? Read
 *       from {@code role_permissions}, so an administrator can change it.</li>
 *   <li><b>Scope</b> — may this particular user act on this particular
 *       project or shaft? A Project Manager holds {@code shafts.edit} but
 *       only for shafts inside their assigned projects.</li>
 * </ul>
 *
 * <p>The JWT carries a permission claim, but it is a snapshot from login and
 * is used only to drive nav visibility in the UI. Every server-side check
 * goes through this service and hits the database, so revoking a permission
 * takes effect on the next request rather than the next login.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** Roles that see the whole group and are never scoped to a subset. */
    private static final List<String> UNSCOPED_ROLES =
            List.of("DIRECTOR", "ADMIN", "EXECUTIVE", "FINANCE", "AUDITOR");

    private final PermissionRepository permissionRepository;
    private final UserProjectAccessRepository projectAccessRepository;
    private final UserShaftAccessRepository shaftAccessRepository;
    private final UserCacheService userCacheService;

    @Cacheable(value = "rolePermissions", key = "#roleId")
    @Transactional(readOnly = true)
    public List<String> permissionCodesForRole(Long roleId) {
        return permissionRepository.findCodesByRoleId(roleId);
    }

    @CacheEvict(value = "rolePermissions", allEntries = true)
    public void evictPermissionCache() {
        // Called whenever role_permissions changes.
    }

    /** The signed-in user, or a 403 if the token names an account that no longer resolves. */
    @Transactional(readOnly = true)
    public User currentUser() {
        String email = AuditContext.currentUser();
        return userCacheService.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Your account could not be resolved"));
    }

    @Transactional(readOnly = true)
    public boolean has(String permissionCode) {
        User user = currentUser();
        return permissionCodesForRole(user.getRoleId()).contains(permissionCode);
    }

    /** Throws 403 unless the caller holds the permission. */
    @Transactional(readOnly = true)
    public void require(String permissionCode) {
        if (!has(permissionCode)) {
            throw new AccessDeniedException("Requires permission: " + permissionCode);
        }
    }

    // ---------------------------------------------------------------- scope

    @Transactional(readOnly = true)
    public boolean isUnscoped(User user, String roleCode) {
        // An unscoped role, or a scoped role with no assignments recorded yet,
        // sees everything. Empty-means-all is a deliberate choice: it keeps a
        // newly created Project Manager from staring at an empty application
        // before anyone has assigned them a project.
        if (UNSCOPED_ROLES.contains(roleCode)) return true;
        return projectAccessRepository.findProjectIdsByUserId(user.getId()).isEmpty()
            && shaftAccessRepository.findShaftIdsByUserId(user.getId()).isEmpty();
    }

    /** Project ids the user may see, or empty list meaning "unrestricted". */
    @Transactional(readOnly = true)
    public List<Long> visibleProjectIds(User user) {
        return projectAccessRepository.findProjectIdsByUserId(user.getId());
    }

    /** Shaft ids the user may see, or empty list meaning "unrestricted". */
    @Transactional(readOnly = true)
    public List<Long> visibleShaftIds(User user) {
        return shaftAccessRepository.findShaftIdsByUserId(user.getId());
    }

    /**
     * Guards a single project. Callers pass the project the record belongs to,
     * not the record — a scoping check that has to load the record first has
     * already leaked whether it exists.
     */
    @Transactional(readOnly = true)
    public void requireProjectAccess(Long projectId, String roleCode) {
        User user = currentUser();
        if (isUnscoped(user, roleCode)) return;
        List<Long> allowed = visibleProjectIds(user);
        if (!allowed.isEmpty() && !allowed.contains(projectId)) {
            throw new AccessDeniedException("You are not assigned to this project");
        }
    }

    @Transactional(readOnly = true)
    public void requireShaftAccess(Long shaftId, Long projectId, String roleCode) {
        User user = currentUser();
        if (isUnscoped(user, roleCode)) return;
        List<Long> allowedShafts = visibleShaftIds(user);
        if (!allowedShafts.isEmpty() && allowedShafts.contains(shaftId)) return;
        List<Long> allowedProjects = visibleProjectIds(user);
        if (!allowedProjects.isEmpty() && allowedProjects.contains(projectId)) return;
        throw new AccessDeniedException("You are not assigned to this shaft");
    }

    /** Convenience for services that need the id of a record that must exist. */
    public static <T> T orNotFound(java.util.Optional<T> value, String entity, Object id) {
        return value.orElseThrow(() -> NotFoundException.of(entity, id));
    }
}
