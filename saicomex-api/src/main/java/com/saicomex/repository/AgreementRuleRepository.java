package com.saicomex.repository;

import com.saicomex.entity.AgreementRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface AgreementRuleRepository extends JpaRepository<AgreementRule, Long> {

    List<AgreementRule> findAllByAgreementIdOrderBySequenceNoAsc(Long agreementId);

    List<AgreementRule> findAllByAgreementIdAndIsActiveTrueOrderBySequenceNoAsc(Long agreementId);

    @Modifying
    @Transactional
    void deleteAllByAgreementId(Long agreementId);

    /** Active rules for an agreement whose effective window covers the given date. */
    @Query("""
           SELECT r FROM AgreementRule r
           WHERE r.agreementId = :agreementId
             AND r.isActive = TRUE
             AND (r.effectiveFrom IS NULL OR r.effectiveFrom <= :onDate)
             AND (r.effectiveTo IS NULL OR r.effectiveTo >= :onDate)
           ORDER BY r.sequenceNo ASC
           """)
    List<AgreementRule> findEffectiveOn(@Param("agreementId") Long agreementId, @Param("onDate") LocalDate onDate);
}
