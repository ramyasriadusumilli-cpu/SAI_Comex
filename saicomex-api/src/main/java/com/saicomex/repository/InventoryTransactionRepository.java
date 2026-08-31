package com.saicomex.repository;

import com.saicomex.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — a reversed
 * (soft-deleted) movement must never re-enter a stock total or a cost report.
 */
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    Optional<InventoryTransaction> findByIdAndDeletedAtIsNull(Long id);

    /** Offline-sync idempotency (Phase 5): replaying a client uuid returns the original row. */
    Optional<InventoryTransaction> findByClientUuidAndDeletedAtIsNull(String clientUuid);

    boolean existsByTransactionNumberIgnoreCase(String transactionNumber);

    long count();

    @Query("""
           SELECT t FROM InventoryTransaction t
           WHERE t.deletedAt IS NULL
             AND (CAST(:itemId AS long) IS NULL OR t.itemId = :itemId)
             AND (CAST(:storeId AS long) IS NULL OR t.storeId = :storeId)
             AND (CAST(:shaftId AS long) IS NULL OR t.shaftId = :shaftId)
             AND (CAST(:type AS string) IS NULL OR t.transactionType = :type)
             AND (CAST(:from AS timestamp) IS NULL OR t.transactionDate >= :from)
             AND (CAST(:to AS timestamp) IS NULL OR t.transactionDate <= :to)
           ORDER BY t.transactionDate DESC
           """)
    Page<InventoryTransaction> search(@Param("itemId") Long itemId,
                                      @Param("storeId") Long storeId,
                                      @Param("shaftId") Long shaftId,
                                      @Param("type") String type,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      Pageable pageable);
}
