package com.saicomex.repository;

import com.saicomex.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<ExpenseCategory> findByCode(String code);

    List<ExpenseCategory> findAllByExpenseClassAndIsActiveTrue(String expenseClass);
}
