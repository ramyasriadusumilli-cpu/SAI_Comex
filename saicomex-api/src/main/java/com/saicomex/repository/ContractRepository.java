package com.saicomex.repository;

import com.saicomex.entity.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByContractNumberIgnoreCaseAndDeletedAtIsNull(String contractNumber);

    List<Contract> findAllByShaftIdAndDeletedAtIsNullOrderByEffectiveDateDesc(Long shaftId);

    List<Contract> findAllByPartnerIdAndDeletedAtIsNullOrderByEffectiveDateDesc(Long partnerId);

    Optional<Contract> findByShaftIdAndStatusAndDeletedAtIsNull(Long shaftId, String status);

    /**
     * The contract governing a given shaft on a given date — what the
     * settlement engine calls to find the period's governing contract. Not
     * tied to status = 'ACTIVE' alone: an expired-but-historical contract
     * still governs its own period.
     */
    @Query("""
           SELECT c FROM Contract c
           WHERE c.shaftId = :shaftId
             AND c.status <> 'DRAFT'
             AND c.effectiveDate <= :onDate
             AND (c.expiryDate IS NULL OR c.expiryDate >= :onDate)
             AND c.deletedAt IS NULL
           ORDER BY c.effectiveDate DESC
           """)
    Optional<Contract> findActiveOn(@Param("shaftId") Long shaftId, @Param("onDate") LocalDate onDate);

    @Query("""
           SELECT c FROM Contract c
           WHERE c.status = 'ACTIVE'
             AND c.expiryDate BETWEEN :from AND :to
             AND c.deletedAt IS NULL
           """)
    List<Contract> findExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByStatusAndDeletedAtIsNull(String status);

    long countByDeletedAtIsNull();

    @Query("""
           SELECT c FROM Contract c
           WHERE c.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR c.status = :status)
             AND (CAST(:projectId AS long) IS NULL OR c.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR c.shaftId = :shaftId)
             AND (CAST(:partnerId AS long) IS NULL OR c.partnerId = :partnerId)
             AND (CAST(:contractTypeId AS long) IS NULL OR c.contractTypeId = :contractTypeId)
             AND (CAST(:search AS string) IS NULL OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                  OR LOWER(c.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
           """)
    Page<Contract> search(@Param("status") String status,
                          @Param("projectId") Long projectId,
                          @Param("shaftId") Long shaftId,
                          @Param("partnerId") Long partnerId,
                          @Param("contractTypeId") Long contractTypeId,
                          @Param("search") String search,
                          Pageable pageable);
}
