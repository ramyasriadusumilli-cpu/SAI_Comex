package com.saicomex.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** SRS §5, §29, §30, §45 — executive dashboard and its drill-down levels. */
public final class DashboardDtos {

    private DashboardDtos() {}

    /** SRS §5.1 — the group KPI tiles. */
    public record ExecutiveDashboard(
            String currency,
            String productionUnit,
            LocalDate periodStart,
            LocalDate periodEnd,

            long totalProjects,
            long activeProjects,
            long suspendedProjects,
            long closedProjects,
            long totalOperations,
            long totalShafts,
            long activeShafts,
            long nonProducingShafts,

            BigDecimal productionToday,
            BigDecimal productionThisWeek,
            BigDecimal productionThisMonth,
            BigDecimal productionYearToDate,
            BigDecimal productionPeriod,

            BigDecimal grossRevenue,
            BigDecimal operatingExpenditure,
            BigDecimal capitalExpenditure,
            BigDecimal netOperatingResult,
            BigDecimal saicomexShare,
            BigDecimal partnerShare,
            BigDecimal outstandingPartnerSettlements,
            BigDecimal outstandingLiabilities,

            long openAlerts,
            long criticalAlerts,
            long pendingApprovals,
            List<String> dataNotes
    ) {}

    /** Drill level 2 — one row per project. Every field is clickable in the UI. */
    public record ProjectPerformance(
            Long projectId,
            String projectCode,
            String projectName,
            String status,
            long shaftCount,
            long activeShaftCount,
            BigDecimal production,
            String productionUnit,
            BigDecimal revenue,
            BigDecimal expenses,
            BigDecimal netResult,
            BigDecimal budgetAmount,
            BigDecimal budgetVariance
    ) {}

    /** Drill level 3 — one row per shaft. The SRS §30 comparison table. */
    public record ShaftPerformance(
            Long shaftId,
            String shaftCode,
            String shaftName,
            Long projectId,
            String projectName,
            String partnerName,
            String status,
            BigDecimal production,
            String productionUnit,
            BigDecimal productionTarget,
            BigDecimal productionVariance,
            BigDecimal revenue,
            BigDecimal expenses,
            BigDecimal netResult,
            BigDecimal costPerUnit,
            BigDecimal partnerPayable,
            LocalDate lastProductionDate
    ) {}

    /** Drill level 4 — expenditure inside one shaft, by category. */
    public record CategoryBreakdown(
            String categoryCode,
            String categoryName,
            String expenseClass,
            BigDecimal amount,
            BigDecimal percentOfTotal
    ) {}

    /** Drill level 5 — the individual transactions behind a category. */
    public record TransactionRef(
            Long id,
            String reference,
            LocalDate date,
            String description,
            String categoryCode,
            BigDecimal amount,
            String currency,
            BigDecimal baseAmount,
            String status,
            String sourceTable
    ) {}

    /** SRS §29 — the KPI panel for a single shaft. */
    public record ShaftKpis(
            Long shaftId,
            String shaftName,
            String currency,
            String productionUnit,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal production,
            BigDecimal revenue,
            BigDecimal expenses,
            BigDecimal netResult,
            BigDecimal costPerUnit,
            BigDecimal fuelCostPerUnit,
            BigDecimal profitMargin,
            BigDecimal partnerPayable,
            BigDecimal partnerOutstanding,
            List<CategoryBreakdown> expenseBreakdown
    ) {}

    /** Status counts for the pie/segment tiles, keyed by status. */
    public record StatusCounts(Map<String, Long> projects,
                               Map<String, Long> operations,
                               Map<String, Long> shafts,
                               Map<String, Long> contracts) {}
}
