package com.saicomex.repository;

import com.saicomex.entity.AgreementRuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgreementRuleTypeRepository extends JpaRepository<AgreementRuleType, String> {

    List<AgreementRuleType> findAllByIsActiveTrueOrderByDefaultSequenceAsc();

    List<AgreementRuleType> findAllByStageAndIsActiveTrueOrderByDefaultSequenceAsc(String stage);
}
