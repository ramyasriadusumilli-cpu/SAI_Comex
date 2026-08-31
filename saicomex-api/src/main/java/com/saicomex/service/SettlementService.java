package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.SettlementDtos.*;
import com.saicomex.engine.*;
import com.saicomex.entity.*;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SRS §12 and §25 — turns a shaft, a period and a contract into a partner
 * statement, and persists the derivation alongside the numbers.
 *
 * <p>The arithmetic itself lives in {@link CommercialCalculationEngine}, which
 * takes no repositories. This service's job is to assemble the engine's input
 * honestly and to write down what came out. Keeping the two apart is what
 * makes the engine testable against the SRS worked example without a database.
 *
 * <p><b>Which contract governs a period.</b> The engine is fed the agreement
 * that was in force during the settlement period, not the one in force today.
 * A contract amended in October must not silently re-price September. That is
 * why {@code findActiveOn} and {@code findEffectiveOn} take the period end
 * date, and why the resolved agreement id is stored on the settlement row.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementLineRepository lineRepository;
    private final SettlementCalculationRepository calculationRepository;
    private final ShaftRepository shaftRepository;
    private final ProjectRepository projectRepository;
    private final PartnerRepository partnerRepository;
    private final ContractRepository contractRepository;
    private final CommercialAgreementRepository agreementRepository;
    private final AgreementRuleRepository ruleRepository;
    private final AgreementRuleTierRepository tierRepository;
    private final SaleRepository saleRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseAllocationRepository allocationRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ProductionRecordRepository productionRepository;
    private final PaymentRepository paymentRepository;
    private final CompanyRepository companyRepository;
    private final CommercialCalculationEngine engine;
    private final PermissionService permissions;
    private final AuditService audit;

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public PageResponse<SettlementSummary> list(String status, Long projectId, Long shaftId, Long partnerId,
                                                LocalDate from, LocalDate to, Pageable pageable) {
        permissions.require("settlements.view");
        Page<Settlement> page = settlementRepository.search(
                blank(status), projectId, shaftId, partnerId, from, to, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public SettlementDetail get(Long id) {
        permissions.require("settlements.view");
        Settlement settlement = load(id);
        permissions.requireShaftAccess(settlement.getShaftId(), settlement.getProjectId(), AuditContext.currentRole());
        return toDetail(settlement);
    }

    @Transactional(readOnly = true)
    public PartnerStatement partnerStatement(Long partnerId) {
        permissions.require("settlements.view");
        Partner partner = partnerRepository.findByIdAndDeletedAtIsNull(partnerId)
                .orElseThrow(() -> NotFoundException.of("Partner", partnerId));

        List<Settlement> settlements =
                settlementRepository.findAllByPartnerIdAndDeletedAtIsNullOrderByPeriodEndDesc(partnerId);

        BigDecimal earned = sum(settlements, Settlement::getPartnerNetPayable);
        BigDecimal paid = sum(settlements, Settlement::getAmountPaid);
        BigDecimal retained = sum(settlements, Settlement::getAmountRetained);
        BigDecimal outstanding = sum(settlements, Settlement::getAmountOutstanding);

        return new PartnerStatement(partner.getId(), partner.getLegalName(),
                partner.getPaymentCurrency() == null ? reportingCurrency() : partner.getPaymentCurrency(),
                earned, paid, retained, outstanding,
                settlements.stream().map(this::toSummary).toList());
    }

    // --------------------------------------------------------------- compute

    /**
     * Dry run: computes and returns the statement without writing anything.
     * The operator sees the full waterfall — including any warnings — before a
     * settlement row exists.
     */
    @Transactional(readOnly = true)
    public PreviewResult preview(SettlementRequest request) {
        permissions.require("settlements.calculate");
        Resolved r = resolve(request);
        CalculationResult result = engine.calculate(r.input());

        return new PreviewResult(
                r.shaft().getId(), r.shaft().getName(), r.partner().getLegalName(),
                r.contract().getContractNumber(), r.agreement().getName(),
                request.periodStart(), request.periodEnd(), r.currency(),
                result.grossRevenue(), result.totalDeductions(), result.netDistributable(),
                result.saicomexShare(), result.partnerShare(), result.partnerAdjustments(),
                result.partnerNetPayable(), r.totalProduction(), r.productionUnit(),
                result.steps().stream().map(SettlementService::toStepDto).toList(),
                result.warnings());
    }

    /**
     * Computes and persists a settlement, together with every source line it
     * consumed and every step of the derivation. Idempotent by period: a second
     * call for the same shaft and period is refused rather than producing a
     * duplicate statement.
     */
    @Transactional
    public SettlementDetail calculate(SettlementRequest request) {
        permissions.require("settlements.create");
        permissions.require("settlements.calculate");

        Resolved r = resolve(request);
        if (settlementRepository.existsOverlappingPeriod(
                r.shaft().getId(), request.periodStart(), request.periodEnd())) {
            throw new BusinessRuleException(
                    "A settlement already covers part of " + request.periodStart() + " to " + request.periodEnd()
                    + " for this shaft. Cancel it first, or settle a different period.");
        }

        CalculationResult result = engine.calculate(r.input());

        Settlement settlement = new Settlement();
        settlement.setCompanyId(companyId());
        settlement.setSettlementNumber(nextSettlementNumber());
        settlement.setProjectId(r.shaft().getProjectId());
        settlement.setMiningOperationId(r.shaft().getMiningOperationId());
        settlement.setShaftId(r.shaft().getId());
        settlement.setPartnerId(r.partner().getId());
        settlement.setContractId(r.contract().getId());
        settlement.setAgreementId(r.agreement().getId());
        settlement.setContractVersionId(r.agreement().getContractVersionId());
        settlement.setPeriodStart(request.periodStart());
        settlement.setPeriodEnd(request.periodEnd());
        settlement.setCurrency(r.currency());
        settlement.setGrossRevenue(result.grossRevenue());
        settlement.setTotalDeductions(result.totalDeductions());
        settlement.setNetDistributable(result.netDistributable());
        settlement.setSaicomexShare(result.saicomexShare().add(result.saicomexAdjustments()));
        settlement.setPartnerShare(result.partnerShare());
        settlement.setPartnerAdjustments(result.partnerAdjustments());
        settlement.setPartnerNetPayable(result.partnerNetPayable());
        settlement.setAmountPaid(BigDecimal.ZERO);
        settlement.setAmountRetained(BigDecimal.ZERO);
        settlement.setAmountOutstanding(result.partnerNetPayable());
        settlement.setTotalProduction(r.totalProduction());
        settlement.setProductionUnit(r.productionUnit());
        settlement.setTotalExpenses(r.totalExpenses());
        settlement.setStatus("CALCULATED");
        settlement.setCalculatedAt(LocalDateTime.now());
        settlement.setCalculatedBy(AuditContext.currentUser());
        settlement.setCalculationHash(hashOf(r, result));
        settlement.setNotes(request.notes());

        Settlement saved = settlementRepository.save(settlement);

        persistSteps(saved.getId(), result, r.currency());
        persistLines(saved.getId(), r);

        audit.recordForShaft("CALCULATE", "SETTLEMENT", saved.getId(), saved.getSettlementNumber(),
                saved.getProjectId(), saved.getShaftId(),
                "Settlement calculated for " + request.periodStart() + " to " + request.periodEnd()
                + " under agreement " + r.agreement().getName()
                + " — partner payable " + result.partnerNetPayable() + " " + r.currency());

        return toDetail(saved);
    }

    /**
     * Recompute in place. Only permitted while the statement is still a draft
     * or merely calculated — once approved, a settlement is a financial record
     * and the correct remedy is a new one, not a quiet edit.
     */
    @Transactional
    public SettlementDetail recalculate(Long id) {
        permissions.require("settlements.calculate");
        Settlement settlement = load(id);
        if (!List.of("DRAFT", "CALCULATED").contains(settlement.getStatus())) {
            throw new BusinessRuleException(
                    "This settlement is " + settlement.getStatus()
                    + " and can no longer be recalculated. Cancel it and create a new one.");
        }

        Resolved r = resolve(new SettlementRequest(settlement.getShaftId(),
                settlement.getPeriodStart(), settlement.getPeriodEnd(), settlement.getNotes()));
        CalculationResult result = engine.calculate(r.input());

        BigDecimal previousPayable = settlement.getPartnerNetPayable();

        settlement.setGrossRevenue(result.grossRevenue());
        settlement.setTotalDeductions(result.totalDeductions());
        settlement.setNetDistributable(result.netDistributable());
        settlement.setSaicomexShare(result.saicomexShare().add(result.saicomexAdjustments()));
        settlement.setPartnerShare(result.partnerShare());
        settlement.setPartnerAdjustments(result.partnerAdjustments());
        settlement.setPartnerNetPayable(result.partnerNetPayable());
        settlement.setAmountOutstanding(result.partnerNetPayable().subtract(nz(settlement.getAmountPaid())));
        settlement.setTotalProduction(r.totalProduction());
        settlement.setTotalExpenses(r.totalExpenses());
        settlement.setCalculatedAt(LocalDateTime.now());
        settlement.setCalculatedBy(AuditContext.currentUser());
        settlement.setCalculationHash(hashOf(r, result));

        Settlement saved = settlementRepository.save(settlement);

        calculationRepository.deleteAllBySettlementId(id);
        lineRepository.deleteAllBySettlementId(id);
        persistSteps(id, result, r.currency());
        persistLines(id, r);

        audit.recordChange("SETTLEMENT", id, saved.getSettlementNumber(),
                "partnerNetPayable", previousPayable, result.partnerNetPayable(), "Recalculated");

        return toDetail(saved);
    }

    @Transactional
    public SettlementDetail approve(Long id, ApprovalRequest request) {
        permissions.require("settlements.approve");
        Settlement settlement = load(id);
        if (!"CALCULATED".equals(settlement.getStatus()) && !"PENDING_APPROVAL".equals(settlement.getStatus())) {
            throw new BusinessRuleException("Only a calculated settlement can be approved (this one is "
                    + settlement.getStatus() + ")");
        }
        settlement.setStatus("APPROVED");
        settlement.setApprovedBy(AuditContext.currentUser());
        settlement.setApprovedAt(LocalDateTime.now());
        settlement.setSettlementDate(LocalDate.now());
        Settlement saved = settlementRepository.save(settlement);

        audit.recordForShaft("APPROVE", "SETTLEMENT", id, saved.getSettlementNumber(),
                saved.getProjectId(), saved.getShaftId(),
                "Settlement approved" + (request != null && request.comments() != null
                        ? " — " + request.comments() : ""));
        return toDetail(saved);
    }

    @Transactional
    public void cancel(Long id, String reason) {
        permissions.require("settlements.edit");
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to cancel a settlement");
        }
        Settlement settlement = load(id);
        if (nz(settlement.getAmountPaid()).signum() > 0) {
            throw new BusinessRuleException(
                    "This settlement has payments against it and cannot be cancelled. Reverse the payments first.");
        }
        settlement.setStatus("CANCELLED");
        settlementRepository.save(settlement);
        audit.recordForShaft("CANCEL", "SETTLEMENT", id, settlement.getSettlementNumber(),
                settlement.getProjectId(), settlement.getShaftId(), "Cancelled — " + reason);
    }

    // ------------------------------------------------------------- assembly

    /** Everything resolved from the request, ready to hand to the engine. */
    private record Resolved(Shaft shaft, Partner partner, Contract contract,
                            CommercialAgreement agreement, CalculationInput input,
                            String currency, BigDecimal totalProduction, String productionUnit,
                            BigDecimal totalExpenses, List<Sale> sales,
                            List<ExpenseAllocation> allocations, List<ProductionRecord> production) {}

    private Resolved resolve(SettlementRequest request) {
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new BusinessRuleException("The period end cannot be before the period start");
        }

        Shaft shaft = shaftRepository.findByIdAndDeletedAtIsNull(request.shaftId())
                .orElseThrow(() -> NotFoundException.of("Shaft", request.shaftId()));
        permissions.requireShaftAccess(shaft.getId(), shaft.getProjectId(), AuditContext.currentRole());

        // The contract in force during the period, not the one in force today.
        Contract contract = contractRepository.findActiveOn(shaft.getId(), request.periodEnd())
                .orElseThrow(() -> new BusinessRuleException(
                        "Shaft " + shaft.getName() + " has no contract covering "
                        + request.periodEnd() + ". A settlement needs the contract that governed the period."));

        CommercialAgreement agreement = agreementRepository.findEffectiveOn(contract.getId(), request.periodEnd())
                .orElseThrow(() -> new BusinessRuleException(
                        "Contract " + contract.getContractNumber() + " has no commercial agreement effective on "
                        + request.periodEnd() + ". Add and activate one before settling."));

        Partner partner = partnerRepository.findByIdAndDeletedAtIsNull(contract.getPartnerId())
                .orElseThrow(() -> NotFoundException.of("Partner", contract.getPartnerId()));

        List<AgreementRule> rules = ruleRepository.findEffectiveOn(agreement.getId(), request.periodEnd());
        Map<Long, List<AgreementRuleTier>> tiers = rules.stream()
                .filter(r -> "TIERED".equals(r.getCalculationMethod()))
                .collect(Collectors.toMap(AgreementRule::getId,
                        r -> tierRepository.findAllByRuleIdOrderByTierNoAsc(r.getId())));

        List<Sale> sales = saleRepository.findForSettlement(shaft.getId(), request.periodStart(), request.periodEnd());
        BigDecimal gross = sales.stream()
                .map(s -> nz(s.getNetBaseAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ExpenseAllocation> allocations =
                allocationRepository.findForSettlement(shaft.getId(), request.periodStart(), request.periodEnd());

        // Costs keyed by category code — the key the agreement rules speak in.
        Map<Long, String> categoryCodes = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getCode));
        Set<String> capexCodes = categoryRepository.findAllByExpenseClassAndIsActiveTrue("CAPEX").stream()
                .map(ExpenseCategory::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, BigDecimal> costsByCategory = new LinkedHashMap<>();
        for (ExpenseAllocation allocation : allocations) {
            Expense expense = expenseRepository.findByIdAndDeletedAtIsNull(allocation.getExpenseId()).orElse(null);
            if (expense == null) continue;
            String code = categoryCodes.getOrDefault(expense.getCategoryId(), "OTHER_OPEX");
            costsByCategory.merge(code, nz(allocation.getBaseAmount()), BigDecimal::add);
        }
        BigDecimal totalExpenses = costsByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProductionRecord> production =
                productionRepository.findApprovedForSettlement(shaft.getId(), request.periodStart(), request.periodEnd());
        BigDecimal totalProduction = production.stream()
                .map(p -> nz(p.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String productionUnit = production.isEmpty() ? shaft.getProductionTargetUnit()
                                                     : production.get(0).getUnitCode();

        String currency = agreement.getCurrency() != null ? agreement.getCurrency()
                : contract.getSettlementCurrency() != null ? contract.getSettlementCurrency()
                : reportingCurrency();

        CalculationInput input = new CalculationInput(
                agreement, rules, tiers,
                request.periodStart(), request.periodEnd(), currency,
                gross, costsByCategory, capexCodes,
                totalProduction, productionUnit);

        return new Resolved(shaft, partner, contract, agreement, input, currency,
                totalProduction, productionUnit, totalExpenses, sales, allocations, production);
    }

    private void persistSteps(Long settlementId, CalculationResult result, String currency) {
        for (CalculationStep step : result.steps()) {
            SettlementCalculation row = new SettlementCalculation();
            row.setSettlementId(settlementId);
            row.setStepNo(step.stepNo());
            row.setStage(step.stage());
            row.setRuleId(step.ruleId());
            row.setRuleType(step.ruleType());
            row.setRuleName(step.ruleName());
            row.setExpression(step.expression());
            row.setInputAmount(step.inputAmount());
            row.setPercentApplied(step.percentApplied());
            row.setRateApplied(step.rateApplied());
            row.setResultAmount(step.resultAmount());
            row.setRunningBalance(step.runningBalance());
            row.setBeneficiary(step.beneficiary());
            row.setCurrency(currency);
            row.setNotes(step.notes());
            calculationRepository.save(row);
        }
    }

    /**
     * Persists the source rows the settlement consumed. This is what makes SRS
     * §57 drill-down real: from a partner statement figure, to the sales and
     * expenses behind it, to the individual transaction.
     */
    private void persistLines(Long settlementId, Resolved r) {
        for (Sale sale : r.sales()) {
            SettlementLine line = new SettlementLine();
            line.setSettlementId(settlementId);
            line.setLineType("REVENUE");
            line.setSourceTable("sales");
            line.setSourceId(sale.getId());
            line.setLineDate(sale.getSaleDate());
            line.setDescription(sale.getSaleNumber() + " — " + sale.getProduct()
                    + " " + nz(sale.getQuantity()) + " " + sale.getUnitCode());
            line.setQuantity(sale.getQuantity());
            line.setUnitCode(sale.getUnitCode());
            line.setAmount(sale.getNetAmount());
            line.setCurrency(sale.getCurrency());
            line.setBaseAmount(nz(sale.getNetBaseAmount()));
            line.setIncluded(true);
            lineRepository.save(line);
        }

        for (ExpenseAllocation allocation : r.allocations()) {
            Expense expense = expenseRepository.findByIdAndDeletedAtIsNull(allocation.getExpenseId()).orElse(null);
            SettlementLine line = new SettlementLine();
            line.setSettlementId(settlementId);
            line.setLineType("EXPENSE");
            line.setSourceTable("expenses");
            line.setSourceId(allocation.getExpenseId());
            line.setLineDate(expense == null ? null : expense.getExpenseDate());
            line.setDescription(expense == null ? "Expense allocation"
                    : expense.getExpenseNumber() + " — " + expense.getDescription());
            line.setCategoryCode(expense == null ? null
                    : categoryRepository.findById(expense.getCategoryId())
                        .map(ExpenseCategory::getCode).orElse(null));
            line.setAmount(allocation.getAmount());
            line.setCurrency(expense == null ? null : expense.getCurrency());
            line.setBaseAmount(nz(allocation.getBaseAmount()));
            line.setIncluded(true);
            lineRepository.save(line);
        }

        for (ProductionRecord record : r.production()) {
            SettlementLine line = new SettlementLine();
            line.setSettlementId(settlementId);
            line.setLineType("PRODUCTION");
            line.setSourceTable("production_records");
            line.setSourceId(record.getId());
            line.setLineDate(record.getProductionDate());
            line.setDescription("Production " + nz(record.getQuantity()) + " " + record.getUnitCode());
            line.setQuantity(record.getQuantity());
            line.setUnitCode(record.getUnitCode());
            line.setAmount(BigDecimal.ZERO);
            line.setBaseAmount(BigDecimal.ZERO);
            line.setIncluded(true);
            lineRepository.save(line);
        }
    }

    /**
     * Fingerprint of the inputs this settlement consumed. Recomputing later
     * against changed inputs yields a different hash, which is how a statement
     * that has silently gone stale can be detected instead of quietly
     * disagreeing with the ledger.
     */
    private String hashOf(Resolved r, CalculationResult result) {
        String material = String.join("|",
                String.valueOf(r.agreement().getId()),
                String.valueOf(r.input().periodStart()),
                String.valueOf(r.input().periodEnd()),
                String.valueOf(r.input().grossRevenue()),
                String.valueOf(new TreeMap<>(r.input().costsByCategoryCode())),
                String.valueOf(r.totalProduction()),
                String.valueOf(result.partnerNetPayable()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;   // SHA-256 is mandatory in every JRE; this cannot happen
        }
    }

    // ---------------------------------------------------------------- mapping

    private Settlement load(Long id) {
        return settlementRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Settlement", id));
    }

    private SettlementSummary toSummary(Settlement s) {
        return new SettlementSummary(
                s.getId(), s.getSettlementNumber(),
                projectName(s.getProjectId()), shaftName(s.getShaftId()), partnerName(s.getPartnerId()),
                s.getPeriodStart(), s.getPeriodEnd(), s.getCurrency(),
                s.getGrossRevenue(), s.getNetDistributable(), s.getSaicomexShare(),
                s.getPartnerNetPayable(), s.getAmountPaid(), s.getAmountOutstanding(), s.getStatus());
    }

    private SettlementDetail toDetail(Settlement s) {
        List<CalculationStepDto> steps = calculationRepository
                .findAllBySettlementIdOrderByStepNoAsc(s.getId()).stream()
                .map(c -> new CalculationStepDto(c.getStepNo(), c.getStage(), c.getRuleId(),
                        c.getRuleType(), c.getRuleName(), c.getExpression(), c.getInputAmount(),
                        c.getPercentApplied(), c.getRateApplied(), c.getResultAmount(),
                        c.getRunningBalance(), c.getBeneficiary(), c.getNotes()))
                .toList();

        List<SettlementLineDto> lines = lineRepository
                .findAllBySettlementIdOrderByLineDateAsc(s.getId()).stream()
                .map(l -> new SettlementLineDto(l.getId(), l.getLineType(), l.getSourceTable(),
                        l.getSourceId(), l.getLineDate(), l.getDescription(), l.getCategoryCode(),
                        l.getQuantity(), l.getUnitCode(), l.getAmount(), l.getCurrency(),
                        l.getBaseAmount(), Boolean.TRUE.equals(l.getIncluded()), l.getExclusionReason()))
                .toList();

        String contractNumber = contractRepository.findById(s.getContractId())
                .map(Contract::getContractNumber).orElse(null);
        String agreementName = agreementRepository.findById(s.getAgreementId())
                .map(CommercialAgreement::getName).orElse(null);

        return new SettlementDetail(
                s.getId(), s.getSettlementNumber(),
                s.getProjectId(), projectName(s.getProjectId()),
                s.getShaftId(), shaftName(s.getShaftId()),
                s.getPartnerId(), partnerName(s.getPartnerId()),
                s.getContractId(), contractNumber,
                s.getAgreementId(), agreementName,
                s.getPeriodStart(), s.getPeriodEnd(), s.getSettlementDate(), s.getCurrency(),
                s.getGrossRevenue(), s.getTotalDeductions(), s.getNetDistributable(),
                s.getSaicomexShare(), s.getPartnerShare(), s.getPartnerAdjustments(),
                s.getPartnerNetPayable(), s.getAmountPaid(), s.getAmountRetained(),
                s.getAmountOutstanding(), s.getTotalProduction(), s.getProductionUnit(),
                s.getTotalExpenses(), s.getStatus(), s.getCalculatedAt(), s.getCalculatedBy(),
                s.getApprovedBy(), s.getApprovedAt(), s.getNotes(), steps, lines);
    }

    private static CalculationStepDto toStepDto(CalculationStep step) {
        return new CalculationStepDto(step.stepNo(), step.stage(), step.ruleId(), step.ruleType(),
                step.ruleName(), step.expression(), step.inputAmount(), step.percentApplied(),
                step.rateApplied(), step.resultAmount(), step.runningBalance(),
                step.beneficiary(), step.notes());
    }

    private String nextSettlementNumber() {
        // Sequential within the year, which is what an auditor expects to see.
        String prefix = "STL-" + LocalDate.now().getYear() + "-";
        long count = settlementRepository.count() + 1;
        String candidate = prefix + String.format("%05d", count);
        while (settlementRepository.existsBySettlementNumberIgnoreCaseAndDeletedAtIsNull(candidate)) {
            count++;
            candidate = prefix + String.format("%05d", count);
        }
        return candidate;
    }

    private String projectName(Long id) {
        return id == null ? null : projectRepository.findById(id).map(Project::getName).orElse(null);
    }

    private String shaftName(Long id) {
        return id == null ? null : shaftRepository.findById(id).map(Shaft::getName).orElse(null);
    }

    private String partnerName(Long id) {
        return id == null ? null : partnerRepository.findById(id).map(Partner::getLegalName).orElse(null);
    }

    private Long companyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private String reportingCurrency() {
        return companyRepository.findAll().stream().findFirst()
                .map(Company::getReportingCurrency).orElse("USD");
    }

    private static BigDecimal sum(List<Settlement> settlements,
                                  java.util.function.Function<Settlement, BigDecimal> field) {
        return settlements.stream()
                .filter(s -> !"CANCELLED".equals(s.getStatus()))
                .map(s -> nz(field.apply(s)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
