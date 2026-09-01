package com.saicomex.repository;

import com.saicomex.entity.MaintenanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

    Optional<MaintenanceRecord> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByJobNumberIgnoreCaseAndDeletedAtIsNull(String jobNumber);

    long count();

    @Query("""
           SELECT m FROM MaintenanceRecord m
           WHERE m.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR m.status = :status)
             AND (CAST(:type AS string) IS NULL OR m.maintenanceType = :type)
             AND (CAST(:equipmentId AS long) IS NULL OR m.equipmentId = :equipmentId)
             AND (CAST(:from AS date) IS NULL OR m.serviceDate >= :from)
             AND (CAST(:to AS date) IS NULL OR m.serviceDate <= :to)
           ORDER BY COALESCE(m.serviceDate, m.reportedDate) DESC, m.id DESC
           """)
    Page<MaintenanceRecord> search(@Param("status") String status,
                                   @Param("type") String type,
                                   @Param("equipmentId") Long equipmentId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   Pageable pageable);
}
