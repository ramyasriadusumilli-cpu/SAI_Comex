package com.saicomex.repository;

import com.saicomex.entity.Shaft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity}.
 */
public interface ShaftRepository extends JpaRepository<Shaft, Long> {

    Optional<Shaft> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    List<Shaft> findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(Long projectId);

    List<Shaft> findAllByMiningOperationIdAndDeletedAtIsNullOrderByNameAsc(Long miningOperationId);

    List<Shaft> findAllByOwnerPartnerIdAndDeletedAtIsNull(Long ownerPartnerId);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(String status);

    @Query("SELECT s.status, COUNT(s) FROM Shaft s WHERE s.deletedAt IS NULL GROUP BY s.status")
    List<Object[]> countByStatus();

    @Query("""
           SELECT s FROM Shaft s
           WHERE s.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR s.status = :status)
             AND (CAST(:projectId AS long) IS NULL OR s.projectId = :projectId)
             AND (CAST(:operationId AS long) IS NULL OR s.miningOperationId = :operationId)
             AND (CAST(:partnerId AS long) IS NULL OR s.ownerPartnerId = :partnerId)
             AND (CAST(:search AS string) IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                  OR LOWER(s.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
             AND (:unrestricted = TRUE OR s.id IN :shaftIds)
           """)
    Page<Shaft> search(@Param("status") String status,
                       @Param("projectId") Long projectId,
                       @Param("operationId") Long operationId,
                       @Param("partnerId") Long partnerId,
                       @Param("search") String search,
                       @Param("unrestricted") boolean unrestricted,
                       @Param("shaftIds") List<Long> shaftIds,
                       Pageable pageable);
}
