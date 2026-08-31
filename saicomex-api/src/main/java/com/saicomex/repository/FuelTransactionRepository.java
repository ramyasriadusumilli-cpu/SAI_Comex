package com.saicomex.repository;

import com.saicomex.entity.FuelTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FuelTransactionRepository extends JpaRepository<FuelTransaction, Long> {

    Optional<FuelTransaction> findByIdAndDeletedAtIsNull(Long id);

    Optional<FuelTransaction> findByClientUuidAndDeletedAtIsNull(String clientUuid);

    long count();

    @Query("""
           SELECT f FROM FuelTransaction f
           WHERE f.deletedAt IS NULL
             AND (CAST(:shaftId AS long) IS NULL OR f.shaftId = :shaftId)
             AND (CAST(:fuelType AS string) IS NULL OR f.fuelType = :fuelType)
             AND (CAST(:type AS string) IS NULL OR f.transactionType = :type)
             AND (CAST(:equipmentId AS long) IS NULL OR f.equipmentId = :equipmentId)
             AND (CAST(:from AS timestamp) IS NULL OR f.transactionDate >= :from)
             AND (CAST(:to AS timestamp) IS NULL OR f.transactionDate <= :to)
           ORDER BY f.transactionDate DESC
           """)
    Page<FuelTransaction> search(@Param("shaftId") Long shaftId,
                                 @Param("fuelType") String fuelType,
                                 @Param("type") String type,
                                 @Param("equipmentId") Long equipmentId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);
}
