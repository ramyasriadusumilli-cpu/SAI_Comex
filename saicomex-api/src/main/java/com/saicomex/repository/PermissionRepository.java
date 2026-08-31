package com.saicomex.repository;

import com.saicomex.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscActionAsc();

    @Query("SELECT p.code FROM Permission p JOIN RolePermission rp ON rp.id.permissionId = p.id WHERE rp.id.roleId = :roleId")
    List<String> findCodesByRoleId(@Param("roleId") Long roleId);
}
