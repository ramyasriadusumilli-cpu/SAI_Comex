package com.saicomex.repository;

import com.saicomex.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, Long entityId,
                                                                       Pageable pageable);

    Page<AuditLog> findAllByUserEmailOrderByOccurredAtDesc(String userEmail, Pageable pageable);

    @Query("""
           SELECT a FROM AuditLog a
           WHERE (CAST(:action AS string) IS NULL OR a.action = :action)
             AND (CAST(:entityType AS string) IS NULL OR a.entityType = :entityType)
             AND (CAST(:userEmail AS string) IS NULL OR a.userEmail = :userEmail)
             AND (CAST(:projectId AS long) IS NULL OR a.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR a.shaftId = :shaftId)
             AND (CAST(:from AS timestamp) IS NULL OR a.occurredAt >= :from)
             AND (CAST(:to AS timestamp) IS NULL OR a.occurredAt <= :to)
           """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("userEmail") String userEmail,
                          @Param("projectId") Long projectId,
                          @Param("shaftId") Long shaftId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);
}
