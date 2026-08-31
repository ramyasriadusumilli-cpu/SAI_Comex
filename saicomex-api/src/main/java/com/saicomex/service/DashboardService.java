package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.DashboardDtos.*;
import com.saicomex.entity.*;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SRS §5, §29, §30, §45 — the executive dashboard and its drill-down path.
 *
 * <p>SRS §57 names traceability as the governing principle: every figure on
 * the dashboard must lead back to the transaction that produced it. That is
 * why this service exposes each level of the hierarchy as its own endpoint
 * returning the same measures — group, project, shaft, category, transaction —
 * rather than one nested blob. Each level's identifiers are the query
 * parameters of the level below it, so the UI drill-down is a navigation, not
 * a second aggregation with its own rounding.
 *
 * <p>All money is reported in the group reporting currency, using the
 * {@code base_amount} column that each transaction froze at entry time.
 * Re-converting historical amounts at today's rate would make last month's
 * dashboard change every morning.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final MiningOperationRepository operationRepository;
    private final ShaftRepository shaftRepository;
    private final PartnerRepository partnerRepository;
    private final ContractRepository contractRepository;
    private final ProductionRecordRepository productionRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseAllocationRepository allocationRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final SaleRepository saleRepository;
    private final SettlementRepository settlementRepository;
    private final AlertRepository alertRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final SystemConfigService config;

    @Value("${app.reporting-currency:USD}")
    private String reportingCurrency;

    // ------------------------------------------------------------ group level

    @Transactional(readOnly = true)
    public ExecutiveDashboard executive(LocalDate from, LocalDate to) {
        permissions.require("dashboard.view");
        LocalDate today = LocalDate.now();
        LocalDate periodStart = from != null ? from : today.withDayOfMonth(1);
        LocalDate periodEnd = to != null ? to : today;

        Map<String, Long> projectStatus = countMap(projectRepository.countByStatus());
        Map<String, Long> shaftStatus = countMap(shaftRepository.countByStatus());

        long totalShafts = shaftRepository.countByDeletedAtIsNull();
        long activeShafts = shaftStatus.getOrDefault("ACTIVE", 0L);

        BigDecimal revenue = nz(saleRepository.sumGrossBaseAmountBetween(periodStart, periodEnd));
        BigDecimal expenses = nz(expenseRepository.sumBaseAmountBetween(periodStart, periodEnd));
        BigDecimal capex = capitalExpenditure(periodStart, periodEnd);
        BigDecimal opex = expenses.subtract(capex).max(BigDecimal.ZERO);

        // Group shares are summed from approved settlements rather than derived
        // from a group-wide percentage: there is no group-wide percentage, which
        // is the whole point of the commercial agreement engine (SRS §11).
        List<Settlement> settlements = settlementRepository.findAll().stream()
                .filter(s -> s.getDeletedAt() == null)
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .filter(s -> !s.getPeriodEnd().isBefore(periodStart) && !s.getPeriodStart().isAfter(periodEnd))
                .toList();

        BigDecimal saicomexShare = settlements.stream()
                .map(s -> nz(s.getSaicomexShare())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal partnerShare = settlements.stream()
                .map(s -> nz(s.getPartnerNetPayable())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> notes = new ArrayList<>();
        if (settlements.isEmpty() && revenue.signum() > 0) {
            notes.add("Revenue has been recorded for this period but no settlement has been calculated yet, "
                    + "so the SAIComex and partner shares read zero.");
        }

        int idleDays = config.getInt("alert.no_production_days", 3);
        long nonProducing = shaftRepository.findAll().stream()
                .filter(s -> s.getDeletedAt() == null && "ACTIVE".equals(s.getStatus()))
                .filter(s -> {
                    LocalDate last = productionRepository.findLastProductionDateByShaft(s.getId());
                    return last == null || last.isBefore(today.minusDays(idleDays));
                })
                .count();

        return new ExecutiveDashboard(
                reportingCurrency(), defaultUnit(), periodStart, periodEnd,
                projectRepository.countByDeletedAtIsNull(),
                projectStatus.getOrDefault("ACTIVE", 0L),
                projectStatus.getOrDefault("SUSPENDED", 0L),
                projectStatus.getOrDefault("CLOSED", 0L),
                operationRepository.countByDeletedAtIsNull(),
                totalShafts, activeShafts, nonProducing,

                nz(productionRepository.sumQuantityBetween(today, today)),
                nz(productionRepository.sumQuantityBetween(today.with(DayOfWeek.MONDAY), today)),
                nz(productionRepository.sumQuantityBetween(today.withDayOfMonth(1), today)),
                nz(productionRepository.sumQuantityBetween(today.withDayOfYear(1), today)),
                nz(productionRepository.sumQuantityBetween(periodStart, periodEnd)),

                revenue, opex, capex, revenue.subtract(expenses),
                saicomexShare, partnerShare,
                nz(settlementRepository.sumOutstandingAll()),
                nz(settlementRepository.sumOutstandingAll()),

                alertRepository.countByStatus("OPEN"),
                alertRepository.countByStatus("OPEN"),
                expenseRepository.countByStatusAndDeletedAtIsNull("PENDING_APPROVAL"),
                notes);
    }

    @Transactional(readOnly = true)
    public StatusCounts statusCounts() {
        permissions.require("dashboard.view");
        return new StatusCounts(
                countMap(projectRepository.countByStatus()),
                countMap(operationRepository.countByStatus()),
                countMap(shaftRepository.countByStatus()),
                Map.of("ACTIVE", contractRepository.countByStatusAndDeletedAtIsNull("ACTIVE"),
                       "DRAFT", contractRepository.countByStatusAndDeletedAtIsNull("DRAFT"),
                       "EXPIRED", contractRepository.countByStatusAndDeletedAtIsNull("EXPIRED"),
                       "PENDING_APPROVAL", contractRepository.countByStatusAndDeletedAtIsNull("PENDING_APPROVAL")));
    }

    // ---------------------------------------------------------- project level

    @Transactional(readOnly = true)
    public List<ProjectPerformance> projectPerformance(LocalDate from, LocalDate to) {
        permissions.require("dashboard.view");
        LocalDate periodStart = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodEnd = to != null ? to : LocalDate.now();

        Map<Long, BigDecimal> production = pairMap(productionRepository.totalsByProject(periodStart, periodEnd));
        Map<Long, BigDecimal> expenses = pairMap(expenseRepository.totalsByProject(periodStart, periodEnd));

        List<Long> scoped = permissions.visibleProjectIds(permissions.currentUser());

        return projectRepository.findAllByDeletedAtIsNullOrderByNameAsc().stream()
                .filter(p -> scoped.isEmpty() || scoped.contains(p.getId()))
                .map(p -> {
                    List<Shaft> shafts = shaftRepository.findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(p.getId());
                    BigDecimal revenue = shafts.stream()
                            .map(s -> nz(saleRepository.sumNetBaseAmountByShaftBetween(s.getId(), periodStart, periodEnd)))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal spend = nz(expenses.get(p.getId()));
                    return new ProjectPerformance(
                            p.getId(), p.getCode(), p.getName(), p.getStatus(),
                            shafts.size(),
                            shafts.stream().filter(s -> "ACTIVE".equals(s.getStatus())).count(),
                            nz(production.get(p.getId())), defaultUnit(),
                            revenue, spend, revenue.subtract(spend),
                            p.getBudgetAmount(),
                            p.getBudgetAmount() == null ? null : p.getBudgetAmount().subtract(spend));
                })
                .toList();
    }

    // ------------------------------------------------------------ shaft level

    /** SRS §30 — the shaft-versus-shaft comparison table. */
    @Transactional(readOnly = true)
    public List<ShaftPerformance> shaftPerformance(Long projectId, LocalDate from, LocalDate to) {
        permissions.require("dashboard.view");
        LocalDate periodStart = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodEnd = to != null ? to : LocalDate.now();

        List<Long> scopedProjects = permissions.visibleProjectIds(permissions.currentUser());
        List<Long> scopedShafts = permissions.visibleShaftIds(permissions.currentUser());

        List<Shaft> shafts = projectId != null
                ? shaftRepository.findAllByProjectIdAndDeletedAtIsNullOrderByNameAsc(projectId)
                : shaftRepository.findAll().stream().filter(s -> s.getDeletedAt() == null).toList();

        return shafts.stream()
                .filter(s -> scopedProjects.isEmpty() || scopedProjects.contains(s.getProjectId())
                          || scopedShafts.contains(s.getId()))
                .map(s -> {
                    BigDecimal production = nz(productionRepository
                            .sumQuantityByShaftBetween(s.getId(), periodStart, periodEnd));
                    BigDecimal revenue = nz(saleRepository
                            .sumNetBaseAmountByShaftBetween(s.getId(), periodStart, periodEnd));
                    BigDecimal spend = nz(allocationRepository
                            .sumBaseAmountByShaftBetween(s.getId(), periodStart, periodEnd));
                    BigDecimal payable = settlementRepository
                            .findAllByShaftIdAndDeletedAtIsNullOrderByPeriodEndDesc(s.getId()).stream()
                            .filter(x -> !"CANCELLED".equals(x.getStatus()))
                            .map(x -> nz(x.getAmountOutstanding()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new ShaftPerformance(
                            s.getId(), s.getCode(), s.getName(),
                            s.getProjectId(), projectName(s.getProjectId()),
                            partnerName(s.getOwnerPartnerId()), s.getStatus(),
                            production,
                            s.getProductionTargetUnit() == null ? defaultUnit() : s.getProductionTargetUnit(),
                            s.getProductionTarget(),
                            s.getProductionTarget() == null ? null : production.subtract(s.getProductionTarget()),
                            revenue, spend, revenue.subtract(spend),
                            perUnit(spend, production), payable,
                            productionRepository.findLastProductionDateByShaft(s.getId()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ShaftKpis shaftKpis(Long shaftId, LocalDate from, LocalDate to) {
        permissions.require("dashboard.view");
        Shaft shaft = shaftRepository.findByIdAndDeletedAtIsNull(shaftId)
                .orElseThrow(() -> NotFoundException.of("Shaft", shaftId));
        permissions.requireShaftAccess(shaftId, shaft.getProjectId(), AuditContext.currentRole());

        LocalDate periodStart = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodEnd = to != null ? to : LocalDate.now();

        BigDecimal production = nz(productionRepository.sumQuantityByShaftBetween(shaftId, periodStart, periodEnd));
        BigDecimal revenue = nz(saleRepository.sumNetBaseAmountByShaftBetween(shaftId, periodStart, periodEnd));
        BigDecimal spend = nz(allocationRepository.sumBaseAmountByShaftBetween(shaftId, periodStart, periodEnd));
        BigDecimal net = revenue.subtract(spend);

        List<CategoryBreakdown> breakdown = expenseBreakdown(shaftId, periodStart, periodEnd);
        BigDecimal fuelSpend = breakdown.stream()
                .filter(c -> Set.of("DIESEL", "PETROL", "OIL").contains(c.categoryCode()))
                .map(CategoryBreakdown::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = settlementRepository
                .findAllByShaftIdAndDeletedAtIsNullOrderByPeriodEndDesc(shaftId).stream()
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .map(s -> nz(s.getAmountOutstanding()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal payable = settlementRepository
                .findAllByShaftIdAndDeletedAtIsNullOrderByPeriodEndDesc(shaftId).stream()
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .filter(s -> !s.getPeriodEnd().isBefore(periodStart) && !s.getPeriodStart().isAfter(periodEnd))
                .map(s -> nz(s.getPartnerNetPayable()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ShaftKpis(shaftId, shaft.getName(), reportingCurrency(),
                shaft.getProductionTargetUnit() == null ? defaultUnit() : shaft.getProductionTargetUnit(),
                periodStart, periodEnd,
                production, revenue, spend, net,
                perUnit(spend, production), perUnit(fuelSpend, production),
                revenue.signum() == 0 ? null
                        : net.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP),
                payable, outstanding, breakdown);
    }

    // --------------------------------------------------------- category level

    @Transactional(readOnly = true)
    public List<CategoryBreakdown> expenseBreakdown(Long shaftId, LocalDate from, LocalDate to) {
        permissions.require("dashboard.view");
        LocalDate periodStart = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate periodEnd = to != null ? to : LocalDate.now();

        Map<Long, ExpenseCategory> categories = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, c -> c));

        Map<Long, BigDecimal> totals = pairMap(
                expenseRepository.totalsByCategoryForShaft(shaftId, periodStart, periodEnd));

        BigDecimal grand = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return totals.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().signum() != 0)
                .map(e -> {
                    ExpenseCategory category = categories.get(e.getKey());
                    return new CategoryBreakdown(
                            category == null ? "UNKNOWN" : category.getCode(),
                            category == null ? "Uncategorised" : category.getName(),
                            category == null ? "OPEX" : category.getExpenseClass(),
                            e.getValue(),
                            grand.signum() == 0 ? BigDecimal.ZERO
                                    : e.getValue().multiply(BigDecimal.valueOf(100))
                                       .divide(grand, 2, RoundingMode.HALF_UP));
                })
                .sorted(Comparator.comparing(CategoryBreakdown::amount).reversed())
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private BigDecimal capitalExpenditure(LocalDate from, LocalDate to) {
        return nz(expenseRepository.sumBaseAmountByExpenseClassBetween("CAPEX", from, to));
    }

    private static BigDecimal perUnit(BigDecimal cost, BigDecimal quantity) {
        if (quantity == null || quantity.signum() == 0) return null;
        return cost.divide(quantity, 4, RoundingMode.HALF_UP);
    }

    private String projectName(Long id) {
        return id == null ? null : projectRepository.findById(id).map(Project::getName).orElse(null);
    }

    private String partnerName(Long id) {
        return id == null ? null : partnerRepository.findById(id).map(Partner::getLegalName).orElse(null);
    }

    private String reportingCurrency() {
        return companyRepository.findAll().stream().findFirst()
                .map(Company::getReportingCurrency).orElse(reportingCurrency);
    }

    private String defaultUnit() {
        return config.getString("group.default_production_unit", "G");
    }

    /** {@code List<Object[]>} of (String status, Long count) → map. */
    private static Map<String, Long> countMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row.length < 2 || row[0] == null) continue;
            map.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return map;
    }

    /** {@code List<Object[]>} of (Long id, BigDecimal total) → map. */
    private static Map<Long, BigDecimal> pairMap(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row.length < 2 || row[0] == null) continue;
            BigDecimal value = row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString());
            map.merge(((Number) row[0]).longValue(), value, BigDecimal::add);
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
