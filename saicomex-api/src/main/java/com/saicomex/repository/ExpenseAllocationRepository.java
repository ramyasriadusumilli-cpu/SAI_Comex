package com.saicomex.repository;

import com.saicomex.entity.Expense;
import com.saicomex.entity.ExpenseAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseAllocationRepository extends JpaRepository<ExpenseAllocation, Long> {

    List<ExpenseAllocation> findAllByExpenseId(Long expenseId);

    @Modifying
    @Transactional
    void deleteAllByExpenseId(Long expenseId);

    /** Allocations for a shaft whose parent expense fell in the period and is approved for settlement. */
    @Query("""
           SELECT a FROM ExpenseAllocation a, Expense e
           WHERE a.expenseId = e.id
             AND a.shaftId = :shaftId
             AND e.status IN ('APPROVED', 'PAID')
             AND e.deletedAt IS NULL
             AND e.expenseDate BETWEEN :from AND :to
           """)
    List<ExpenseAllocation> findForSettlement(@Param("shaftId") Long shaftId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    @Query("""
           SELECT COALESCE(SUM(a.baseAmount), 0) FROM ExpenseAllocation a, Expense e
           WHERE a.expenseId = e.id
             AND a.shaftId = :shaftId
             AND e.status IN ('APPROVED', 'PAID')
             AND e.deletedAt IS NULL
             AND e.expenseDate BETWEEN :from AND :to
           """)
    BigDecimal sumBaseAmountByShaftBetween(@Param("shaftId") Long shaftId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);
}
