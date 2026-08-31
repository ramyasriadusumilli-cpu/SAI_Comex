package com.saicomex.repository;

import com.saicomex.entity.MiningOperation;
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
public interface MiningOperationRepository extends JpaRepository<MiningOperation, Long> {

    Optional<MiningOperation> findByIdAndDeletedAtIsNull(Long id);

    List<MiningOperation> findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(Long projectId);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    long countByDeletedAtIsNull();

    @Query("SELECT m.status, COUNT(m) FROM MiningOperation m WHERE m.deletedAt IS NULL GROUP BY m.status")
    List<Object[]> countByStatus();

    @Query("""
           SELECT m FROM MiningOperation m
           WHERE m.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR m.status = :status)
             AND (CAST(:projectId AS long) IS NULL OR m.projectId = :projectId)
             AND (CAST(:search AS string) IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                  OR LOWER(m.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
             AND (:unrestricted = TRUE OR m.projectId IN :projectIds)
           """)
    Page<MiningOperation> search(@Param("status") String status,
                                 @Param("projectId") Long projectId,
                                 @Param("search") String search,
                                 @Param("unrestricted") boolean unrestricted,
                                 @Param("projectIds") List<Long> projectIds,
                                 Pageable pageable);
}
