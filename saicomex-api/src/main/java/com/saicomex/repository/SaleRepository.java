package com.saicomex.repository;

import com.saicomex.entity.Sale;
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
public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdAndDeletedAtIsNull(Long id);

    boolean existsBySaleNumberIgnoreCaseAndDeletedAtIsNull(String saleNumber);

    @Query("""
           SELECT COALESCE(SUM(s.netBaseAmount), 0) FROM Sale s
           WHERE s.shaftId = :shaftId AND s.status = 'CONFIRMED' AND s.deletedAt IS NULL
             AND s.saleDate BETWEEN :from AND :to
           """)
    BigDecimal sumNetBaseAmountByShaftBetween(@Param("shaftId") Long shaftId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    @Query("""
           SELECT COALESCE(SUM(s.grossBaseAmount), 0) FROM Sale s
           WHERE s.status = 'CONFIRMED' AND s.deletedAt IS NULL
             AND s.saleDate BETWEEN :from AND :to
           """)
    BigDecimal sumGrossBaseAmountBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT s.shaftId, SUM(s.netBaseAmount) FROM Sale s
           WHERE s.status = 'CONFIRMED' AND s.deletedAt IS NULL
             AND s.saleDate BETWEEN :from AND :to
           GROUP BY s.shaftId
           """)
    List<Object[]> totalsByShaft(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT s FROM Sale s
           WHERE s.shaftId = :shaftId AND s.status = 'CONFIRMED' AND s.settlementStatus = 'UNSETTLED'
             AND s.deletedAt IS NULL AND s.saleDate BETWEEN :from AND :to
           """)
    List<Sale> findForSettlement(@Param("shaftId") Long shaftId,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    @Query("""
           SELECT s FROM Sale s
           WHERE s.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR s.status = :status)
             AND (CAST(:projectId AS long) IS NULL OR s.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR s.shaftId = :shaftId)
             AND (CAST(:buyerId AS long) IS NULL OR s.buyerId = :buyerId)
             AND (CAST(:from AS date) IS NULL OR s.saleDate >= :from)
             AND (CAST(:to AS date) IS NULL OR s.saleDate <= :to)
           """)
    Page<Sale> search(@Param("status") String status,
                      @Param("projectId") Long projectId,
                      @Param("shaftId") Long shaftId,
                      @Param("buyerId") Long buyerId,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to,
                      Pageable pageable);
}
