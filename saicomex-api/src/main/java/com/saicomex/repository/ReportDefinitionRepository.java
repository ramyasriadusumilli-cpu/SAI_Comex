package com.saicomex.repository;

import com.saicomex.entity.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {

    List<ReportDefinition> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<ReportDefinition> findAllByReportGroupAndIsActiveTrueOrderByDisplayOrderAsc(String reportGroup);
}
