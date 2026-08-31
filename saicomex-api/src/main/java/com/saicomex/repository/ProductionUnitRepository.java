package com.saicomex.repository;

import com.saicomex.entity.ProductionUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionUnitRepository extends JpaRepository<ProductionUnit, String> {

    List<ProductionUnit> findAllByIsActiveTrueOrderByDisplayOrderAsc();
}
