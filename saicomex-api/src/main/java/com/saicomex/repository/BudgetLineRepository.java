package com.saicomex.repository;

import com.saicomex.entity.BudgetLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {

    List<BudgetLine> findAllByBudgetIdOrderByLineNoAsc(Long budgetId);

    @Modifying
    @Transactional
    void deleteAllByBudgetId(Long budgetId);
}
