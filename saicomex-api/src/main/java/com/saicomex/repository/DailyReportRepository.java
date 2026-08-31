package com.saicomex.repository;

import com.saicomex.entity.DailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity}.
 */
public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    Optional<DailyReport> findByIdAndDeletedAtIsNull(Long id);

    Page<DailyReport> findAllByShaftIdAndDeletedAtIsNullOrderByReportDateDesc(Long shaftId, Pageable pageable);

    boolean existsByShaftIdAndReportDateAndDeletedAtIsNull(Long shaftId, LocalDate reportDate);

    @Query("""
           SELECT r FROM DailyReport r
           WHERE r.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR r.status = :status)
             AND (CAST(:projectId AS long) IS NULL OR r.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR r.shaftId = :shaftId)
             AND (CAST(:from AS date) IS NULL OR r.reportDate >= :from)
             AND (CAST(:to AS date) IS NULL OR r.reportDate <= :to)
           """)
    Page<DailyReport> search(@Param("status") String status,
                             @Param("projectId") Long projectId,
                             @Param("shaftId") Long shaftId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             Pageable pageable);
}
