package com.saicomex.repository;

import com.saicomex.entity.StoreLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StoreLocationRepository extends JpaRepository<StoreLocation, Long> {

    Optional<StoreLocation> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("""
           SELECT s FROM StoreLocation s
           WHERE (CAST(:active AS boolean) IS NULL OR s.isActive = :active)
             AND (CAST(:storeType AS string) IS NULL OR s.storeType = :storeType)
             AND (CAST(:shaftId AS long) IS NULL OR s.shaftId = :shaftId)
           ORDER BY s.name
           """)
    List<StoreLocation> search(@Param("active") Boolean active,
                               @Param("storeType") String storeType,
                               @Param("shaftId") Long shaftId);
}
