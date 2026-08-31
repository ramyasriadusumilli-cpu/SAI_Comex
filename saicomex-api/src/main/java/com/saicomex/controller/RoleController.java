package com.saicomex.controller;

import com.saicomex.dto.RoleDtos.ModulePermissions;
import com.saicomex.dto.RoleDtos.RoleDetail;
import com.saicomex.dto.RoleDtos.RoleRequest;
import com.saicomex.dto.RoleDtos.RoleSummary;
import com.saicomex.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SRS §37 — {@code /api/roles}.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public List<RoleSummary> list() {
        return roleService.list();
    }

    /** The full permission catalogue, grouped by module, for the permission matrix UI. */
    @GetMapping("/permissions")
    public List<ModulePermissions> permissions() {
        return roleService.permissionCatalogue();
    }

    @GetMapping("/{id}")
    public RoleDetail get(@PathVariable Long id) {
        return roleService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDetail create(@Valid @RequestBody RoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/{id}")
    public RoleDetail update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
