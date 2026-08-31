package com.saicomex.repository;

import com.saicomex.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity}.
 */
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByExpenseNumberIgnoreCaseAndDeletedAtIsNull(String expenseNumber);

    /** SRS §33 offline sync — lets create() replay a client uuid idempotently. */
    Optional<Expense> findByClientUuidAndDeletedAtIsNull(String clientUuid);

    @Query("""
           SELECT COALESCE(SUM(e.baseAmount), 0) FROM Expense e
           WHERE e.shaftId = :shaftId AND e.status IN ('APPROVED', 'PAID') AND e.deletedAt IS NULL
             AND e.expenseDate BETWEEN :from AND :to
           """)
    BigDecimal sumBaseAmountByShaftBetween(@Param("shaftId") Long shaftId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    @Query("""
           SELECT COALESCE(SUM(e.baseAmount), 0) FROM Expense e
           WHERE e.status IN ('APPROVED', 'PAID') AND e.deletedAt IS NULL
             AND e.expenseDate BETWEEN :from AND :to
           """)
    BigDecimal sumBaseAmountBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT e.categoryId, SUM(e.baseAmount) FROM Expense e
           WHERE e.shaftId = :shaftId AND e.status IN ('APPROVED', 'PAID') AND e.deletedAt IS NULL
             AND e.expenseDate BETWEEN :from AND :to
           GROUP BY e.categoryId
           """)
    List<Object[]> totalsByCategoryForShaft(@Param("shaftId") Long shaftId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("""
           SELECT e.projectId, SUM(e.baseAmount) FROM Expense e
           WHERE e.status IN ('APPROVED', 'PAID') AND e.deletedAt IS NULL
             AND e.expenseDate BETWEEN :from AND :to
           GROUP BY e.projectId
           """)
    List<Object[]> totalsByProject(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Group spend for one expense class (OPEX / CAPEX). Joined through the
     * category rather than duplicating the class onto every expense row —
     * reclassifying a category must not require rewriting its history.
     */
    @Query("""
           SELECT COALESCE(SUM(e.baseAmount), 0) FROM Expense e, ExpenseCategory c
           WHERE c.id = e.categoryId AND c.expenseClass = :expenseClass
             AND e.status IN ('APPROVED', 'PAID') AND e.deletedAt IS NULL
             AND e.expenseDate BETWEEN :from AND :to
           """)
    BigDecimal sumBaseAmountByExpenseClassBetween(@Param("expenseClass") String expenseClass,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    long countByStatusAndDeletedAtIsNull(String status);

    @Query("""
           SELECT e FROM Expense e
           WHERE e.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR e.status = :status)
             AND (CAST(:projectId AS long) IS NULL OR e.projectId = :projectId)
             AND (CAST(:shaftId AS long) IS NULL OR e.shaftId = :shaftId)
             AND (CAST(:categoryId AS long) IS NULL OR e.categoryId = :categoryId)
             AND (CAST(:from AS date) IS NULL OR e.expenseDate >= :from)
             AND (CAST(:to AS date) IS NULL OR e.expenseDate <= :to)
             AND (CAST(:search AS string) IS NULL OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                  OR LOWER(e.expenseNumber) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
           """)
    Page<Expense> search(@Param("status") String status,
                         @Param("projectId") Long projectId,
                         @Param("shaftId") Long shaftId,
                         @Param("categoryId") Long categoryId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("search") String search,
                         Pageable pageable);
}
