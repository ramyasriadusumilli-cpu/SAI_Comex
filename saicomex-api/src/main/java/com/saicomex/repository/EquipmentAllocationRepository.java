package com.saicomex.repository;

import com.saicomex.entity.EquipmentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentAllocationRepository extends JpaRepository<EquipmentAllocation, Long> {

    List<EquipmentAllocation> findByEquipmentIdOrderByFromDateDescIdDesc(Long equipmentId);

    /** The current placement — the single open (to_date IS NULL) row, if any. */
    Optional<EquipmentAllocation> findByEquipmentIdAndToDateIsNull(Long equipmentId);
}
