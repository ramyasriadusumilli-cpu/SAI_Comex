package com.saicomex.repository;

import com.saicomex.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Page<Alert> findAllByStatusOrderByTriggeredAtDesc(String status, Pageable pageable);

    long countByStatus(String status);

    boolean existsByDedupeKeyAndStatus(String dedupeKey, String status);

    Page<Alert> findAllByShaftIdOrderByTriggeredAtDesc(Long shaftId, Pageable pageable);

    @Query("""
           SELECT a FROM Alert a
           WHERE (CAST(:status AS string) IS NULL OR a.status = :status)
             AND (CAST(:severity AS string) IS NULL OR a.severity = :severity)
             AND (CAST(:category AS string) IS NULL OR a.category = :category)
             AND (CAST(:projectId AS long) IS NULL OR a.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR a.shaftId = :shaftId)
           """)
    Page<Alert> search(@Param("status") String status,
                       @Param("severity") String severity,
                       @Param("category") String category,
                       @Param("projectId") Long projectId,
                       @Param("shaftId") Long shaftId,
                       Pageable pageable);
}
