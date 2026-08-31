package com.saicomex.repository;

import com.saicomex.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findAllByIsActiveTrueOrderByCategoryAsc();

    Optional<AlertRule> findByCode(String code);

    List<AlertRule> findAllByCategoryAndIsActiveTrue(String category);
}
