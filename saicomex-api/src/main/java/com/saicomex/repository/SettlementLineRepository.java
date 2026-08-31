package com.saicomex.repository;

import com.saicomex.entity.SettlementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SettlementLineRepository extends JpaRepository<SettlementLine, Long> {

    List<SettlementLine> findAllBySettlementIdOrderByLineDateAsc(Long settlementId);

    List<SettlementLine> findAllBySettlementIdAndLineType(Long settlementId, String lineType);

    @Modifying
    @Transactional
    void deleteAllBySettlementId(Long settlementId);
}
