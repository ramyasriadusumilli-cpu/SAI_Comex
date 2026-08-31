package com.saicomex.repository;

import com.saicomex.entity.Partner;
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
public interface PartnerRepository extends JpaRepository<Partner, Long> {

    Optional<Partner> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    List<Partner> findAllByDeletedAtIsNullOrderByLegalNameAsc();

    long countByDeletedAtIsNull();

    @Query("""
           SELECT p FROM Partner p
           WHERE p.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR p.status = :status)
             AND (CAST(:search AS string) IS NULL OR LOWER(p.legalName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                  OR LOWER(p.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
           """)
    Page<Partner> search(@Param("status") String status,
                         @Param("search") String search,
                         Pageable pageable);
}
