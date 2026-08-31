package com.saicomex.repository;

import com.saicomex.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity}.
 */
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByIdAndDeletedAtIsNull(Long id);

    List<Budget> findAllByDeletedAtIsNullOrderByFiscalYearDesc();

    List<Budget> findAllByProjectIdAndDeletedAtIsNull(Long projectId);
}
