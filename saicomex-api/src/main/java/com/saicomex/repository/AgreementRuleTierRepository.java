package com.saicomex.repository;

import com.saicomex.entity.AgreementRuleTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AgreementRuleTierRepository extends JpaRepository<AgreementRuleTier, Long> {

    List<AgreementRuleTier> findAllByRuleIdOrderByTierNoAsc(Long ruleId);

    @Modifying
    @Transactional
    void deleteAllByRuleId(Long ruleId);
}
