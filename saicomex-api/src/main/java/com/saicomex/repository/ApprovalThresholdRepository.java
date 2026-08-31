package com.saicomex.repository;

import com.saicomex.entity.ApprovalThreshold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalThresholdRepository extends JpaRepository<ApprovalThreshold, Long> {

    List<ApprovalThreshold> findAllByEntityTypeAndIsActiveTrueOrderByStepNoAscMinAmountAsc(String entityType);
}
