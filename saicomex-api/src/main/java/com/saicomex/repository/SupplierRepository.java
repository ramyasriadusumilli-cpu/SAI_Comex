package com.saicomex.repository;

import com.saicomex.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    List<Supplier> findByDeletedAtIsNullOrderByName();

    @Query("""
           SELECT s FROM Supplier s
           WHERE s.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR s.status = :status)
             AND (CAST(:type AS string) IS NULL OR s.supplierType = :type)
             AND (CAST(:search AS string) IS NULL
                  OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                  OR LOWER(s.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
           ORDER BY s.name
           """)
    Page<Supplier> search(@Param("status") String status,
                          @Param("type") String type,
                          @Param("search") String search,
                          Pageable pageable);
}
