package com.saicomex.repository;

import com.saicomex.entity.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity}.
 */
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByIdAndDeletedAtIsNull(Long id);

    boolean existsBySettlementNumberIgnoreCaseAndDeletedAtIsNull(String settlementNumber);

    List<Settlement> findAllByShaftIdAndDeletedAtIsNullOrderByPeriodEndDesc(Long shaftId);

    List<Settlement> findAllByPartnerIdAndDeletedAtIsNullOrderByPeriodEndDesc(Long partnerId);

    @Query("""
           SELECT COALESCE(SUM(s.amountOutstanding), 0) FROM Settlement s
           WHERE s.partnerId = :partnerId AND s.deletedAt IS NULL
           """)
    BigDecimal sumOutstandingByPartner(@Param("partnerId") Long partnerId);

    @Query("SELECT COALESCE(SUM(s.amountOutstanding), 0) FROM Settlement s WHERE s.deletedAt IS NULL")
    BigDecimal sumOutstandingAll();

    /** Whether an active (non-cancelled) settlement already covers any part of the given period for this shaft. */
    @Query("""
           SELECT CASE WHEN COUNT(s) > 0 THEN TRUE ELSE FALSE END FROM Settlement s
           WHERE s.shaftId = :shaftId AND s.deletedAt IS NULL AND s.status <> 'CANCELLED'
             AND s.periodStart <= :to AND s.periodEnd >= :from
           """)
    boolean existsOverlappingPeriod(@Param("shaftId") Long shaftId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    @Query("""
           SELECT s FROM Settlement s
           WHERE s.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR s.status = :status)
             AND (CAST(:projectId AS long) IS NULL OR s.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR s.shaftId = :shaftId)
             AND (CAST(:partnerId AS long) IS NULL OR s.partnerId = :partnerId)
             AND (CAST(:from AS date) IS NULL OR s.periodEnd >= :from)
             AND (CAST(:to AS date) IS NULL OR s.periodStart <= :to)
           """)
    Page<Settlement> search(@Param("status") String status,
                            @Param("projectId") Long projectId,
                            @Param("shaftId") Long shaftId,
                            @Param("partnerId") Long partnerId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            Pageable pageable);
}
