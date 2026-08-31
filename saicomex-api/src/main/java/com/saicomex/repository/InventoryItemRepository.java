package com.saicomex.repository;

import com.saicomex.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
           SELECT i FROM InventoryItem i
           WHERE (CAST(:itemType AS string) IS NULL OR i.itemType = :itemType)
             AND (CAST(:active AS boolean) IS NULL OR i.isActive = :active)
             AND (CAST(:controlled AS boolean) IS NULL OR i.isControlled = :controlled)
             AND (CAST(:search AS string) IS NULL
                  OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                  OR LOWER(i.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
           """)
    Page<InventoryItem> search(@Param("itemType") String itemType,
                               @Param("active") Boolean active,
                               @Param("controlled") Boolean controlled,
                               @Param("search") String search,
                               Pageable pageable);
}
