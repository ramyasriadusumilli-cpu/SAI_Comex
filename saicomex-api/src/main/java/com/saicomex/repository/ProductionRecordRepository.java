package com.saicomex.repository;

import com.saicomex.entity.ProductionRecord;
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
public interface ProductionRecordRepository extends JpaRepository<ProductionRecord, Long> {

    Optional<ProductionRecord> findByIdAndDeletedAtIsNull(Long id);

    /** SRS §33 offline sync — lets create() replay a client uuid idempotently. */
    Optional<ProductionRecord> findByClientUuidAndDeletedAtIsNull(String clientUuid);

    Page<ProductionRecord> findAllByShaftIdAndDeletedAtIsNullOrderByProductionDateDesc(Long shaftId, Pageable pageable);

    @Query("""
           SELECT COALESCE(SUM(p.quantity), 0) FROM ProductionRecord p
           WHERE p.shaftId = :shaftId AND p.status = 'APPROVED' AND p.deletedAt IS NULL
             AND p.productionDate BETWEEN :from AND :to
           """)
    BigDecimal sumQuantityByShaftBetween(@Param("shaftId") Long shaftId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    @Query("""
           SELECT COALESCE(SUM(p.quantity), 0) FROM ProductionRecord p
           WHERE p.status = 'APPROVED' AND p.deletedAt IS NULL
             AND p.productionDate BETWEEN :from AND :to
           """)
    BigDecimal sumQuantityBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT p.shaftId, SUM(p.quantity) FROM ProductionRecord p
           WHERE p.status = 'APPROVED' AND p.deletedAt IS NULL
             AND p.productionDate BETWEEN :from AND :to
           GROUP BY p.shaftId
           """)
    List<Object[]> totalsByShaft(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT p.projectId, SUM(p.quantity) FROM ProductionRecord p
           WHERE p.status = 'APPROVED' AND p.deletedAt IS NULL
             AND p.productionDate BETWEEN :from AND :to
           GROUP BY p.projectId
           """)
    List<Object[]> totalsByProject(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT p FROM ProductionRecord p
           WHERE p.shaftId = :shaftId AND p.status = 'APPROVED' AND p.deletedAt IS NULL
             AND p.productionDate BETWEEN :from AND :to
           ORDER BY p.productionDate ASC
           """)
    List<ProductionRecord> findApprovedForSettlement(@Param("shaftId") Long shaftId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    @Query("""
           SELECT MAX(p.productionDate) FROM ProductionRecord p
           WHERE p.shaftId = :shaftId AND p.deletedAt IS NULL
           """)
    LocalDate findLastProductionDateByShaft(@Param("shaftId") Long shaftId);

    @Query("""
           SELECT p FROM ProductionRecord p
           WHERE p.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR p.status = :status)
             AND (CAST(:projectId AS long) IS NULL OR p.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR p.shaftId = :shaftId)
             AND (CAST(:from AS date) IS NULL OR p.productionDate >= :from)
             AND (CAST(:to AS date) IS NULL OR p.productionDate <= :to)
           """)
    Page<ProductionRecord> search(@Param("status") String status,
                                  @Param("projectId") Long projectId,
                                  @Param("shaftId") Long shaftId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  Pageable pageable);
}
