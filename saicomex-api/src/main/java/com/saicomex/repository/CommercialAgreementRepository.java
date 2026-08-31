package com.saicomex.repository;

import com.saicomex.entity.CommercialAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity} for why this is not a
 * {@code @Where} annotation.
 */
public interface CommercialAgreementRepository extends JpaRepository<CommercialAgreement, Long> {

    Optional<CommercialAgreement> findByIdAndDeletedAtIsNull(Long id);

    List<CommercialAgreement> findAllByContractIdAndDeletedAtIsNullOrderByEffectiveFromDesc(Long contractId);

    Optional<CommercialAgreement> findByContractIdAndStatusAndDeletedAtIsNull(Long contractId, String status);

    /** The agreement in force for a contract on a given date. */
    @Query("""
           SELECT a FROM CommercialAgreement a
           WHERE a.contractId = :contractId
             AND a.deletedAt IS NULL
             AND a.status <> 'DRAFT'
             AND a.effectiveFrom <= :onDate
             AND (a.effectiveTo IS NULL OR a.effectiveTo >= :onDate)
           ORDER BY a.effectiveFrom DESC
           """)
    Optional<CommercialAgreement> findEffectiveOn(@Param("contractId") Long contractId, @Param("onDate") LocalDate onDate);
}
