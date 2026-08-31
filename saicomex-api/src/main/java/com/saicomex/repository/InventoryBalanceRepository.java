package com.saicomex.repository;

import com.saicomex.entity.InventoryBalance;
import com.saicomex.entity.InventoryBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, InventoryBalanceId> {

    @Query("SELECT b FROM InventoryBalance b WHERE b.id.storeId = :storeId ORDER BY b.id.itemId")
    List<InventoryBalance> findByStore(@Param("storeId") Long storeId);

    @Query("SELECT b FROM InventoryBalance b WHERE b.id.itemId = :itemId ORDER BY b.id.storeId")
    List<InventoryBalance> findByItem(@Param("itemId") Long itemId);
}
