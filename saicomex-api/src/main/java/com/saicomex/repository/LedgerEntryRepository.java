package com.saicomex.repository;

import com.saicomex.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * SRS §24 — the append-only ledger. No soft delete: entries are corrected by
 * writing a reversal that points back via {@code reversalOfId}, never edited.
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findAllBySourceTableAndSourceId(String sourceTable, Long sourceId);

    @Query("""
           SELECT l.entryType, SUM(l.baseAmount) FROM LedgerEntry l
           WHERE l.entryDate BETWEEN :from AND :to
           GROUP BY l.entryType
           """)
    List<Object[]> totalsByTypeBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    Page<LedgerEntry> findByShaftIdOrderByEntryDateDesc(Long shaftId, Pageable pageable);
}
