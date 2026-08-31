package com.saicomex.service;

import com.saicomex.dto.RoleDtos.ModulePermissions;
import com.saicomex.dto.RoleDtos.PermissionDto;
import com.saicomex.dto.RoleDtos.RoleDetail;
import com.saicomex.dto.RoleDtos.RoleRequest;
import com.saicomex.dto.RoleDtos.RoleSummary;
import com.saicomex.entity.Permission;
import com.saicomex.entity.Role;
import com.saicomex.entity.RolePermission;
import com.saicomex.entity.RolePermissionId;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.PermissionRepository;
import com.saicomex.repository.RolePermissionRepository;
import com.saicomex.repository.RoleRepository;
import com.saicomex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SRS §37 — configurable roles and the permission grants behind them.
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRepository userRepository;
    private final PermissionService permissions;
    private final UserCacheService userCacheService;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<RoleSummary> list() {
        permissions.require("roles.view");
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getDisplayOrder).thenComparing(Role::getName))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDetail get(Long id) {
        permissions.require("roles.view");
        return toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public List<ModulePermissions> permissionCatalogue() {
        permissions.require("roles.view");
        List<Permission> all = permissionRepository.findAllByOrderByModuleAscActionAsc();
        Map<String, List<PermissionDto>> byModule = new LinkedHashMap<>();
        for (Permission p : all) {
            byModule.computeIfAbsent(p.getModule(), m -> new java.util.ArrayList<>())
                    .add(new PermissionDto(p.getCode(), p.getModule(), p.getAction(), p.getDescription()));
        }
        return byModule.entrySet().stream()
                .map(e -> new ModulePermissions(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional
    public RoleDetail create(RoleRequest req) {
        permissions.require("roles.create");
        validateCodeUnique(req.code(), null);

        Role role = new Role();
        apply(role, req);
        role.setIsSystem(false);
        Role saved = roleRepository.save(role);

        replacePermissions(saved.getId(), req.permissionCodes());
        evictPermissionCaches();

        audit.record("CREATE", "ROLE", saved.getId(), saved.getCode(), "Role " + saved.getName() + " created");
        return toDetail(saved);
    }

    @Transactional
    public RoleDetail update(Long id, RoleRequest req) {
        permissions.require("roles.edit");
        Role role = load(id);

        if (Boolean.TRUE.equals(role.getIsSystem()) && !role.getCode().equalsIgnoreCase(req.code())) {
            throw new BusinessRuleException("The code of a system role (" + role.getCode() + ") cannot be changed");
        }
        validateCodeUnique(req.code(), id);

        audit.recordChange("ROLE", id, role.getCode(), "isActive", role.getIsActive(), req.isActive(), null);
        apply(role, req);
        Role saved = roleRepository.save(role);

        replacePermissions(saved.getId(), req.permissionCodes());
        evictPermissionCaches();

        return toDetail(saved);
    }

    @Transactional
    public void delete(Long id) {
        permissions.require("roles.delete");
        Role role = load(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessRuleException("System role " + role.getCode() + " cannot be deleted");
        }
        long userCount = userRepository.search(null, id, null, Pageable.unpaged()).getTotalElements();
        if (userCount > 0) {
            throw new BusinessRuleException(
                    "Role " + role.getCode() + " is still assigned to " + userCount
                    + " user(s) and cannot be deleted. Reassign them first.");
        }

        roleRepository.delete(role); // role_permissions cascade at the database level
        evictPermissionCaches();

        audit.record("DELETE", "ROLE", id, role.getCode(), "Role " + role.getName() + " deleted");
    }

    // ---------------------------------------------------------------- helpers

    private Role load(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Role", id));
    }

    private void validateCodeUnique(String code, Long existingId) {
        roleRepository.findAll().stream()
                .filter(r -> r.getCode().equalsIgnoreCase(code) && !r.getId().equals(existingId))
                .findFirst()
                .ifPresent(r -> {
                    throw new BusinessRuleException("Role code " + code + " is already in use by " + r.getName());
                });
    }

    private void apply(Role role, RoleRequest req) {
        role.setCode(req.code());
        role.setName(req.name());
        role.setDescription(req.description());
        role.setIsActive(req.isActive());
        role.setDisplayOrder(req.displayOrder());
    }

    /**
     * Replaces the whole permission grant set for a role. Unknown codes are
     * rejected outright rather than silently dropped, so a typo in the SPA
     * cannot quietly leave a role with fewer permissions than intended.
     */
    private void replacePermissions(Long roleId, List<String> permissionCodes) {
        List<String> codes = permissionCodes == null ? List.of() : permissionCodes;
        List<Permission> all = permissionRepository.findAllByOrderByModuleAscActionAsc();
        Map<String, Long> idByCode = new LinkedHashMap<>();
        all.forEach(p -> idByCode.put(p.getCode(), p.getId()));

        List<String> unknown = codes.stream().filter(c -> !idByCode.containsKey(c)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException("Unknown permission code(s): " + String.join(", ", unknown));
        }

        rolePermissionRepository.deleteAllByIdRoleId(roleId);
        codes.stream().distinct().forEach(code -> {
            RolePermission rp = new RolePermission();
            rp.setId(new RolePermissionId(roleId, idByCode.get(code)));
            rolePermissionRepository.save(rp);
        });
    }

    /**
     * A role's permission set changing must invalidate {@code rolePermissions}
     * (the cache the check actually reads) and, per {@link UserCacheService},
     * {@code userActive} too — every session for that role is affected, and
     * this keeps invalidation uniform with "this user changed" rather than
     * leaving a second, differently-invalidated cache to reason about.
     */
    private void evictPermissionCaches() {
        permissions.evictPermissionCache();
        userCacheService.evictAll();
    }

    private RoleSummary toSummary(Role r) {
        int userCount = (int) userRepository.search(null, r.getId(), null, Pageable.unpaged()).getTotalElements();
        int permissionCount = permissions.permissionCodesForRole(r.getId()).size();
        return new RoleSummary(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getIsSystem(), r.getIsActive(), userCount, permissionCount);
    }

    private RoleDetail toDetail(Role r) {
        List<String> codes = permissions.permissionCodesForRole(r.getId());
        int userCount = (int) userRepository.search(null, r.getId(), null, Pageable.unpaged()).getTotalElements();
        return new RoleDetail(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                r.getIsSystem(), r.getIsActive(), userCount, codes.size(), r.getDisplayOrder(), codes,
                r.getCreatedAt(), r.getCreatedBy(), r.getUpdatedAt(), r.getUpdatedBy());
    }
}
