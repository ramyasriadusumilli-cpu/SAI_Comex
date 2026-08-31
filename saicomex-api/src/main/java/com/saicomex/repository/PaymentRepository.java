package com.saicomex.repository;

import com.saicomex.entity.Payment;
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
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByPaymentNumberIgnoreCaseAndDeletedAtIsNull(String paymentNumber);

    List<Payment> findAllBySettlementIdAndDeletedAtIsNull(Long settlementId);

    @Query("""
           SELECT COALESCE(SUM(p.baseAmount), 0) FROM Payment p
           WHERE p.settlementId = :settlementId AND p.status = 'PAID' AND p.deletedAt IS NULL
           """)
    BigDecimal sumPaidBySettlement(@Param("settlementId") Long settlementId);

    @Query("""
           SELECT COALESCE(SUM(p.baseAmount), 0) FROM Payment p
           WHERE p.partnerId = :partnerId AND p.status = 'PAID' AND p.deletedAt IS NULL
           """)
    BigDecimal sumPaidByPartner(@Param("partnerId") Long partnerId);

    @Query("""
           SELECT p FROM Payment p
           WHERE p.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR p.status = :status)
             AND (CAST(:paymentType AS string) IS NULL OR p.paymentType = :paymentType)
             AND (CAST(:partnerId AS long) IS NULL OR p.partnerId = :partnerId)
             AND (CAST(:projectId AS long) IS NULL OR p.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR p.shaftId = :shaftId)
             AND (CAST(:from AS date) IS NULL OR p.paymentDate >= :from)
             AND (CAST(:to AS date) IS NULL OR p.paymentDate <= :to)
           """)
    Page<Payment> search(@Param("status") String status,
                         @Param("paymentType") String paymentType,
                         @Param("partnerId") Long partnerId,
                         @Param("projectId") Long projectId,
                         @Param("shaftId") Long shaftId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         Pageable pageable);
}
