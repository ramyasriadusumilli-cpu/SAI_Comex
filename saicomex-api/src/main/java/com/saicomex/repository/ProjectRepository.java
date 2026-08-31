package com.saicomex.repository;

import com.saicomex.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity} for why this is not a
 * {@code @Where} annotation.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndDeletedAtIsNull(Long id);

    Optional<Project> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    List<Project> findAllByDeletedAtIsNullOrderByNameAsc();

    @Query("""
           SELECT p FROM Project p
           WHERE p.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR p.status = :status)
             AND (CAST(:type AS string) IS NULL OR p.projectType = :type)
             AND (CAST(:search AS string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                  OR LOWER(p.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
             AND (:unrestricted = TRUE OR p.id IN :projectIds)
           """)
    Page<Project> search(@Param("status") String status,
                         @Param("type") String type,
                         @Param("search") String search,
                         @Param("unrestricted") boolean unrestricted,
                         @Param("projectIds") List<Long> projectIds,
                         Pageable pageable);

    @Query("SELECT p.status, COUNT(p) FROM Project p WHERE p.deletedAt IS NULL GROUP BY p.status")
    List<Object[]> countByStatus();

    long countByDeletedAtIsNull();
}
