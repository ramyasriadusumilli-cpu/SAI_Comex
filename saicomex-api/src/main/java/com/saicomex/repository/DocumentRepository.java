package com.saicomex.repository;

import com.saicomex.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity}.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndDeletedAtIsNull(Long id);

    List<Document> findAllByEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(String entityType,
                                                                                        Long entityId);

    long countByEntityTypeAndEntityIdAndDeletedAtIsNull(String entityType, Long entityId);

    @Query("""
           SELECT d FROM Document d
           WHERE d.deletedAt IS NULL
             AND d.expiryDate IS NOT NULL
             AND d.expiryDate <= :date
           """)
    List<Document> findExpiringBefore(@Param("date") LocalDate date);
}
