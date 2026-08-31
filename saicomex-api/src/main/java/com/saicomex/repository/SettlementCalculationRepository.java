package com.saicomex.repository;

import com.saicomex.entity.SettlementCalculation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SettlementCalculationRepository extends JpaRepository<SettlementCalculation, Long> {

    List<SettlementCalculation> findAllBySettlementIdOrderByStepNoAsc(Long settlementId);

    @Modifying
    @Transactional
    void deleteAllBySettlementId(Long settlementId);
}
