package com.saicomex.repository;

import com.saicomex.entity.Equipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByAssetNumberIgnoreCaseAndDeletedAtIsNull(String assetNumber);

    @Query("""
           SELECT e FROM Equipment e
           WHERE e.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR e.status = :status)
             AND (CAST(:type AS string) IS NULL OR e.equipmentType = :type)
             AND (CAST(:shaftId AS long) IS NULL OR e.shaftId = :shaftId)
             AND (CAST(:search AS string) IS NULL
                  OR LOWER(e.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                  OR LOWER(e.assetNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
           ORDER BY e.assetNumber
           """)
    Page<Equipment> search(@Param("status") String status,
                           @Param("type") String type,
                           @Param("shaftId") Long shaftId,
                           @Param("search") String search,
                           Pageable pageable);
}
