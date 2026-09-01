package com.saicomex.repository;

import com.saicomex.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByPoNumberIgnoreCaseAndDeletedAtIsNull(String poNumber);

    long count();

    @Query("""
           SELECT p FROM PurchaseOrder p
           WHERE p.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR p.status = :status)
             AND (CAST(:supplierId AS long) IS NULL OR p.supplierId = :supplierId)
             AND (CAST(:projectId AS long) IS NULL OR p.projectId = :projectId)
             AND (CAST(:from AS date) IS NULL OR p.orderDate >= :from)
             AND (CAST(:to AS date) IS NULL OR p.orderDate <= :to)
           ORDER BY p.orderDate DESC, p.id DESC
           """)
    Page<PurchaseOrder> search(@Param("status") String status,
                               @Param("supplierId") Long supplierId,
                               @Param("projectId") Long projectId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               Pageable pageable);
}
