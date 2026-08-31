package com.saicomex.repository;

import com.saicomex.entity.RolePermission;
import com.saicomex.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findAllByIdRoleId(Long roleId);

    @Modifying
    @Transactional
    void deleteAllByIdRoleId(Long roleId);
}
