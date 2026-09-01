package com.saicomex.repository;

import com.saicomex.entity.MaintenancePart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenancePartRepository extends JpaRepository<MaintenancePart, Long> {

    List<MaintenancePart> findByMaintenanceRecordId(Long maintenanceRecordId);
}
