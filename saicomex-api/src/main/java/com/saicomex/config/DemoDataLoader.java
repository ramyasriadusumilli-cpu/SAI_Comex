package com.saicomex.config;

import com.saicomex.entity.AgreementRule;
import com.saicomex.entity.Buyer;
import com.saicomex.entity.CommercialAgreement;
import com.saicomex.entity.Company;
import com.saicomex.entity.Contract;
import com.saicomex.entity.Expense;
import com.saicomex.entity.ExpenseAllocation;
import com.saicomex.entity.ExpenseCategory;
import com.saicomex.entity.MiningOperation;
import com.saicomex.entity.Partner;
import com.saicomex.entity.ProductionRecord;
import com.saicomex.entity.Project;
import com.saicomex.entity.Sale;
import com.saicomex.entity.Shaft;
import com.saicomex.entity.User;
import com.saicomex.repository.AgreementRuleRepository;
import com.saicomex.repository.BuyerRepository;
import com.saicomex.repository.CommercialAgreementRepository;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.ContractRepository;
import com.saicomex.repository.ExpenseAllocationRepository;
import com.saicomex.repository.ExpenseCategoryRepository;
import com.saicomex.repository.ExpenseRepository;
import com.saicomex.repository.MiningOperationRepository;
import com.saicomex.repository.PartnerRepository;
import com.saicomex.repository.ProductionRecordRepository;
import com.saicomex.repository.ProjectRepository;
import com.saicomex.repository.SaleRepository;
import com.saicomex.repository.ShaftRepository;
import com.saicomex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Seeds a small, realistic demo dataset so a fresh install's dashboard is not
 * an empty screen. Guarded twice over: the {@code app.demo-data.enabled}
 * property (default {@code false}) must be on, AND the database must have no
 * projects yet — the second guard is the one that actually matters, since it
 * means this can never duplicate data onto a real, in-use system even if the
 * flag is accidentally left on.
 *
 * <p>Writes go straight through the repositories, not the service layer: an
 * {@link ApplicationRunner} has no authenticated caller for {@code
 * PermissionService} to check, and it should not need one to seed a
 * demonstration. Every row is stamped {@code created_by = "demo-data"}
 * explicitly (rather than left to default to {@code system}) so it is
 * unmistakable in the audit trail which records are seeded and which are
 * real user activity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(10)
public class DemoDataLoader implements ApplicationRunner {

    private static final String DEMO_USER = "demo-data";
    private static final String CURRENCY = "USD";
    private static final String UNIT = "G";
    /** SRS-realistic gold price used to derive sale values. */
    private static final BigDecimal GOLD_PRICE_PER_GRAM = new BigDecimal("75.00");

    private final ProjectRepository projectRepository;
    private final MiningOperationRepository operationRepository;
    private final ShaftRepository shaftRepository;
    private final PartnerRepository partnerRepository;
    private final ContractRepository contractRepository;
    private final CommercialAgreementRepository agreementRepository;
    private final AgreementRuleRepository ruleRepository;
    private final ProductionRecordRepository productionRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseAllocationRepository allocationRepository;
    private final BuyerRepository buyerRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    @Value("${app.demo-data.enabled:false}")
    private boolean demoDataEnabled;

    private final Random random = new Random(20260830L);

    @Override
    public void run(ApplicationArguments args) {
        if (!demoDataEnabled) {
            return;
        }
        if (projectRepository.count() > 0) {
            log.info("Demo data: skipped — the database already has project data.");
            return;
        }
        Optional<Company> company = companyRepository.findAll().stream().findFirst();
        if (company.isEmpty()) {
            log.warn("Demo data: skipped — no company row exists (see StartupValidator).");
            return;
        }
        Long companyId = company.get().getId();
        Long userId = userRepository.findAllByDeletedAtIsNullOrderByLastNameAsc().stream()
                .filter(u -> "ACTIVE".equals(u.getStatus()))
                .map(User::getId)
                .findFirst()
                .orElse(null);

        log.info("Demo data: seeding a starter dataset (app.demo-data.enabled=true, no projects found)...");

        List<Project> projects = seedProjects(companyId);
        List<MiningOperation> operations = seedOperations(projects);
        List<Shaft> shafts = seedShafts(projects, operations);
        List<Partner> partners = seedPartners(companyId);
        List<Contract> contracts = seedContractsAndAgreements(companyId, shafts, partners);
        int productionCount = seedProduction(shafts, userId);
        int expenseCount = seedExpenses(companyId, shafts, userId);
        List<Buyer> buyers = seedBuyers(companyId);
        int saleCount = seedSales(companyId, shafts, contracts, buyers);

        log.info("Demo data: seeded {} projects, {} operations, {} shafts, {} partners, {} contracts, "
                + "{} production records, {} expenses (with allocations), {} sales.",
                projects.size(), operations.size(), shafts.size(), partners.size(), contracts.size(),
                productionCount, expenseCount, saleCount);
        log.info("Demo data: to disable, set DEMO_DATA_ENABLED=false (or app.demo-data.enabled=false) — "
                + "existing seeded rows are left in place either way, since only an empty projects table triggers seeding.");
    }

    // ---------------------------------------------------------------- hierarchy

    private List<Project> seedProjects(Long companyId) {
        Project saiBotha = newProject(companyId, "PRJ-SAIBOTHA", "SaiBotha", "Mazowe District, Zimbabwe");
        Project mazowe = newProject(companyId, "PRJ-MAZOWE", "Mazowe", "Mazowe Valley, Zimbabwe");
        return List.of(projectRepository.save(saiBotha), projectRepository.save(mazowe));
    }

    private Project newProject(Long companyId, String code, String name, String location) {
        Project p = new Project();
        p.setCompanyId(companyId);
        p.setCode(code);
        p.setName(name);
        p.setProjectType("SHAFT_MINING");
        p.setLocationName(location);
        p.setStartDate(LocalDate.now().minusMonths(8));
        p.setStatus("ACTIVE");
        p.setBudgetCurrency(CURRENCY);
        p.setCreatedBy(DEMO_USER);
        return p;
    }

    private List<MiningOperation> seedOperations(List<Project> projects) {
        return projects.stream().map(p -> {
            MiningOperation op = new MiningOperation();
            op.setProjectId(p.getId());
            op.setCode(p.getCode() + "-OPS");
            op.setName(p.getName() + " Operations");
            op.setOperationType("SHAFT_MINING");
            op.setStartDate(p.getStartDate());
            op.setStatus("ACTIVE");
            op.setCreatedBy(DEMO_USER);
            return operationRepository.save(op);
        }).toList();
    }

    private List<Shaft> seedShafts(List<Project> projects, List<MiningOperation> operations) {
        Project saiBotha = projects.get(0);
        Project mazowe = projects.get(1);
        MiningOperation saiBothaOps = operations.get(0);
        MiningOperation mazoweOps = operations.get(1);

        Shaft s1 = newShaft(saiBotha, saiBothaOps, "SFT-SB1", "SaiBotha Shaft 1");
        Shaft s2 = newShaft(saiBotha, saiBothaOps, "SFT-SB2", "SaiBotha Shaft 2");
        Shaft s3 = newShaft(mazowe, mazoweOps, "SFT-MZ1", "Mazowe Shaft 1");
        Shaft s4 = newShaft(mazowe, mazoweOps, "SFT-MZ2", "Mazowe Shaft 2");

        return List.of(shaftRepository.save(s1), shaftRepository.save(s2),
                shaftRepository.save(s3), shaftRepository.save(s4));
    }

    private Shaft newShaft(Project project, MiningOperation operation, String code, String name) {
        Shaft s = new Shaft();
        s.setProjectId(project.getId());
        s.setMiningOperationId(operation.getId());
        s.setCode(code);
        s.setName(name);
        s.setStatus("ACTIVE");
        s.setStartDate(project.getStartDate());
        // 800–1200 g/month, the range the SRS worked examples use for a shaft.
        s.setProductionTarget(BigDecimal.valueOf(800 + random.nextInt(401)));
        s.setProductionTargetUnit(UNIT);
        s.setProductionTargetPeriod("MONTHLY");
        s.setCreatedBy(DEMO_USER);
        return s;
    }

    private List<Partner> seedPartners(Long companyId) {
        Partner p1 = newPartner(companyId, "PTN-001", "Chiedza Mining Syndicate", "COMPANY");
        Partner p2 = newPartner(companyId, "PTN-002", "Rusike Cooperative", "COOPERATIVE");
        Partner p3 = newPartner(companyId, "PTN-003", "Kudakwashe Ventures (Pvt) Ltd", "COMPANY");
        return List.of(partnerRepository.save(p1), partnerRepository.save(p2), partnerRepository.save(p3));
    }

    private Partner newPartner(Long companyId, String code, String legalName, String type) {
        Partner p = new Partner();
        p.setCompanyId(companyId);
        p.setCode(code);
        p.setLegalName(legalName);
        p.setPartnerType(type);
        p.setCountry("Zimbabwe");
        p.setPaymentCurrency(CURRENCY);
        p.setPaymentMethod("EFT");
        p.setStatus("ACTIVE");
        p.setOnboardedDate(LocalDate.now().minusMonths(8));
        p.setCreatedBy(DEMO_USER);
        return p;
    }

    // ---------------------------------------------------------------- contracts

    /**
     * Three shafts get a contract, each with a different commercial split —
     * 70/30, 80/20 and 60/40 — and each split shows up twice: once as the
     * REVENUE_SHARE allocation rule, and once as the OPEX_SHARE deduction
     * rule taken before that split (SRS §11's deduct-before-split pattern).
     * The fourth shaft is deliberately left uncontracted, which is itself a
     * realistic state for a demo (a shaft awaiting a deal).
     */
    private List<Contract> seedContractsAndAgreements(Long companyId, List<Shaft> shafts, List<Partner> partners) {
        record Split(Shaft shaft, Partner partner, int saicomexPct, int partnerPct) {}
        List<Split> splits = List.of(
                new Split(shafts.get(0), partners.get(0), 70, 30),
                new Split(shafts.get(1), partners.get(1), 80, 20),
                new Split(shafts.get(2), partners.get(2), 60, 40));

        return splits.stream().map(split -> {
            Shaft shaft = split.shaft();
            Contract contract = new Contract();
            contract.setContractNumber("CTR-" + LocalDate.now().getYear() + "-" + String.format("%05d", shaft.getId()));
            contract.setCompanyId(companyId);
            contract.setProjectId(shaft.getProjectId());
            contract.setMiningOperationId(shaft.getMiningOperationId());
            contract.setShaftId(shaft.getId());
            contract.setPartnerId(split.partner().getId());
            contract.setTitle(split.partner().getLegalName() + " — " + shaft.getName() + " tribute agreement");
            contract.setEffectiveDate(LocalDate.now().minusMonths(6));
            contract.setStatus("ACTIVE");
            contract.setSettlementCurrency(CURRENCY);
            contract.setSettlementFrequency("MONTHLY");
            contract.setApprovedBy(DEMO_USER);
            contract.setApprovedAt(LocalDateTime.now());
            contract.setCreatedBy(DEMO_USER);
            Contract savedContract = contractRepository.save(contract);

            shaft.setOwnerPartnerId(split.partner().getId());
            shaftRepository.save(shaft);

            CommercialAgreement agreement = new CommercialAgreement();
            agreement.setContractId(savedContract.getId());
            agreement.setName(split.partner().getLegalName() + " Commercial Agreement");
            agreement.setEffectiveFrom(savedContract.getEffectiveDate());
            agreement.setStatus("ACTIVE");
            agreement.setSettlementBasis("NET_REVENUE");
            agreement.setCurrency(CURRENCY);
            agreement.setRoundingScale((short) 2);
            agreement.setRoundingMode("HALF_UP");
            agreement.setApprovedBy(DEMO_USER);
            agreement.setApprovedAt(LocalDateTime.now());
            agreement.setCreatedBy(DEMO_USER);
            CommercialAgreement savedAgreement = agreementRepository.save(agreement);

            BigDecimal saicomexPct = BigDecimal.valueOf(split.saicomexPct());
            BigDecimal partnerPct = BigDecimal.valueOf(split.partnerPct());

            AgreementRule opexShare = new AgreementRule();
            opexShare.setAgreementId(savedAgreement.getId());
            opexShare.setRuleType("OPEX_SHARE");
            opexShare.setName("Operating expense share");
            opexShare.setSequenceNo(10);
            opexShare.setScope("ALL");
            opexShare.setCalculationMethod("PERCENTAGE");
            opexShare.setSaicomexPercent(saicomexPct);
            opexShare.setPartnerPercent(partnerPct);
            opexShare.setBorneBy("SHARED");
            opexShare.setDeductBeforeSplit(true);
            opexShare.setIsActive(true);
            opexShare.setCreatedBy(DEMO_USER);
            ruleRepository.save(opexShare);

            AgreementRule revenueShare = new AgreementRule();
            revenueShare.setAgreementId(savedAgreement.getId());
            revenueShare.setRuleType("REVENUE_SHARE");
            revenueShare.setName("Revenue share");
            revenueShare.setSequenceNo(210);
            revenueShare.setScope("ALL");
            revenueShare.setCalculationMethod("PERCENTAGE");
            revenueShare.setSaicomexPercent(saicomexPct);
            revenueShare.setPartnerPercent(partnerPct);
            revenueShare.setBorneBy("SHARED");
            revenueShare.setDeductBeforeSplit(false);
            revenueShare.setIsActive(true);
            revenueShare.setCreatedBy(DEMO_USER);
            ruleRepository.save(revenueShare);

            return savedContract;
        }).toList();
    }

    // ---------------------------------------------------------------- production

    /** ~40 APPROVED records: 10 per shaft, roughly weekly, over the last 60 days. */
    private int seedProduction(List<Shaft> shafts, Long userId) {
        int count = 0;
        for (Shaft shaft : shafts) {
            BigDecimal monthlyTarget = shaft.getProductionTarget() == null
                    ? BigDecimal.valueOf(1000) : shaft.getProductionTarget();
            BigDecimal periodTarget = monthlyTarget.multiply(BigDecimal.valueOf(6))
                    .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);

            for (int i = 0; i < 10; i++) {
                LocalDate date = LocalDate.now().minusDays(59L - i * 6L);
                BigDecimal grade = BigDecimal.valueOf(4.5 + random.nextDouble() * 3.0).setScale(4, RoundingMode.HALF_UP);
                BigDecimal recoveryPercent = BigDecimal.valueOf(85 + random.nextInt(10)).setScale(4, RoundingMode.HALF_UP);
                // A little variance either side of target, so the dashboard
                // shows a realistic mix of over- and under-target weeks.
                BigDecimal quantity = periodTarget
                        .multiply(BigDecimal.valueOf(0.85 + random.nextDouble() * 0.3))
                        .setScale(4, RoundingMode.HALF_UP);
                BigDecimal oreTonnes = quantity
                        .divide(grade.multiply(recoveryPercent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP),
                                4, RoundingMode.HALF_UP);

                ProductionRecord record = new ProductionRecord();
                record.setCompanyId(shaftCompanyId(shaft));
                record.setProjectId(shaft.getProjectId());
                record.setMiningOperationId(shaft.getMiningOperationId());
                record.setShaftId(shaft.getId());
                record.setProductionDate(date);
                record.setPeriodType("WEEKLY");
                record.setOreTonnes(oreTonnes);
                record.setGrade(grade);
                record.setRecoveryPercent(recoveryPercent);
                record.setGoldRecovered(quantity);
                record.setQuantity(quantity);
                record.setUnitCode(UNIT);
                record.setProcessingOutput(quantity);
                record.setTargetQuantity(periodTarget);
                record.setVarianceQuantity(quantity.subtract(periodTarget));
                record.setStatus("APPROVED");
                record.setRecordedByUserId(userId);
                record.setVerifiedByUserId(userId);
                record.setVerifiedAt(LocalDateTime.now());
                record.setApprovedByUserId(userId);
                record.setApprovedAt(LocalDateTime.now());
                record.setSource("WEB");
                record.setCreatedBy(DEMO_USER);
                productionRepository.save(record);
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- expenses

    private static final List<String> EXPENSE_CATEGORY_CODES =
            List.of("DIESEL", "LABOUR", "EXPLOSIVES", "EQUIPMENT", "PROCESSING", "TRANSPORT", "SECURITY", "PPE");

    /** ~30 APPROVED expenses, DIRECT-allocated, each with its one required allocation row. */
    private int seedExpenses(Long companyId, List<Shaft> shafts, Long userId) {
        int count = 0;
        int number = 1;
        for (Shaft shaft : shafts) {
            for (String categoryCode : EXPENSE_CATEGORY_CODES) {
                Optional<ExpenseCategory> category = categoryRepository.findByCode(categoryCode);
                if (category.isEmpty()) continue;

                LocalDate date = LocalDate.now().minusDays(1 + (number * 2) % 58);
                BigDecimal amount = amountFor(categoryCode);

                Expense expense = new Expense();
                expense.setCompanyId(companyId);
                expense.setExpenseNumber("EXP-" + LocalDate.now().getYear() + "-" + String.format("%05d", number));
                expense.setProjectId(shaft.getProjectId());
                expense.setMiningOperationId(shaft.getMiningOperationId());
                expense.setShaftId(shaft.getId());
                expense.setCategoryId(category.get().getId());
                expense.setExpenseDate(date);
                expense.setDescription(category.get().getName() + " — " + shaft.getName());
                expense.setAmount(amount);
                expense.setCurrency(CURRENCY);
                expense.setExchangeRate(BigDecimal.ONE);
                expense.setBaseAmount(amount);
                expense.setTaxAmount(BigDecimal.ZERO);
                expense.setAllocationMethod("DIRECT");
                expense.setIsShared(false);
                expense.setStatus("APPROVED");
                expense.setApprovedByUserId(userId);
                expense.setApprovedAt(LocalDateTime.now());
                expense.setSource("WEB");
                expense.setCreatedBy(DEMO_USER);
                Expense saved = expenseRepository.save(expense);

                // Every expense gets an allocation row, DIRECT included — see
                // ExpenseService for why this matters to the settlement engine.
                ExpenseAllocation allocation = new ExpenseAllocation();
                allocation.setExpenseId(saved.getId());
                allocation.setProjectId(saved.getProjectId());
                allocation.setMiningOperationId(saved.getMiningOperationId());
                allocation.setShaftId(shaft.getId());
                allocation.setAllocationPercent(BigDecimal.valueOf(100));
                allocation.setAmount(amount);
                allocation.setBaseAmount(amount);
                allocation.setBasisNote("Direct expense — single shaft");
                allocation.setCreatedBy(DEMO_USER);
                allocationRepository.save(allocation);

                number++;
                count++;
            }
        }
        return count;
    }

    private BigDecimal amountFor(String categoryCode) {
        // [min, max) in USD — realistic per-transaction spend for a small shaft.
        double min, max;
        switch (categoryCode) {
            case "DIESEL" -> { min = 300; max = 900; }
            case "LABOUR" -> { min = 1200; max = 3500; }
            case "EXPLOSIVES" -> { min = 400; max = 1000; }
            case "EQUIPMENT" -> { min = 600; max = 1800; }
            case "PROCESSING" -> { min = 500; max = 1400; }
            case "TRANSPORT" -> { min = 200; max = 600; }
            case "SECURITY" -> { min = 300; max = 800; }
            default -> { min = 100; max = 400; }
        }
        double value = min + random.nextDouble() * (max - min);
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- sales

    private List<Buyer> seedBuyers(Long companyId) {
        Buyer b1 = new Buyer();
        b1.setCompanyId(companyId);
        b1.setCode("BUY-001");
        b1.setName("Fidelity Printers and Refiners");
        b1.setBuyerType("REFINERY");
        b1.setDefaultCurrency(CURRENCY);
        b1.setStatus("ACTIVE");
        b1.setCreatedBy(DEMO_USER);

        Buyer b2 = new Buyer();
        b2.setCompanyId(companyId);
        b2.setCode("BUY-002");
        b2.setName("Metals Trading Co");
        b2.setBuyerType("TRADER");
        b2.setDefaultCurrency(CURRENCY);
        b2.setStatus("ACTIVE");
        b2.setCreatedBy(DEMO_USER);

        return List.of(buyerRepository.save(b1), buyerRepository.save(b2));
    }

    /** ~6 CONFIRMED sales, cycling through all four shafts and both buyers. */
    private int seedSales(Long companyId, List<Shaft> shafts, List<Contract> contracts, List<Buyer> buyers) {
        int count = 0;
        for (int i = 0; i < 6; i++) {
            Shaft shaft = shafts.get(i % shafts.size());
            Buyer buyer = buyers.get(i % buyers.size());
            Long contractId = contracts.stream()
                    .filter(c -> c.getShaftId().equals(shaft.getId()))
                    .map(Contract::getId)
                    .findFirst().orElse(null);

            LocalDate date = LocalDate.now().minusDays(4 + i * 9L);
            BigDecimal quantity = BigDecimal.valueOf(150 + random.nextInt(251)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal unitPrice = GOLD_PRICE_PER_GRAM
                    .add(BigDecimal.valueOf(-3 + random.nextDouble() * 6)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal gross = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
            BigDecimal royalty = gross.multiply(new BigDecimal("0.05")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal deductions = gross.multiply(new BigDecimal("0.01")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(royalty).subtract(deductions);

            Sale sale = new Sale();
            sale.setCompanyId(companyId);
            sale.setSaleNumber("SAL-" + LocalDate.now().getYear() + "-" + String.format("%05d", i + 1));
            sale.setProjectId(shaft.getProjectId());
            sale.setMiningOperationId(shaft.getMiningOperationId());
            sale.setShaftId(shaft.getId());
            sale.setContractId(contractId);
            sale.setBuyerId(buyer.getId());
            sale.setSaleDate(date);
            sale.setProduct("GOLD");
            sale.setQuantity(quantity);
            sale.setUnitCode(UNIT);
            sale.setUnitPrice(unitPrice);
            sale.setCurrency(CURRENCY);
            sale.setExchangeRate(BigDecimal.ONE);
            sale.setGrossAmount(gross);
            sale.setDeductionsAmount(deductions);
            sale.setRoyaltyAmount(royalty);
            sale.setNetAmount(net);
            sale.setGrossBaseAmount(gross);
            sale.setNetBaseAmount(net);
            sale.setInvoiceNumber("INV-" + LocalDate.now().getYear() + "-" + String.format("%04d", i + 1));
            sale.setStatus("CONFIRMED");
            sale.setCreatedBy(DEMO_USER);
            saleRepository.save(sale);
            count++;
        }
        return count;
    }

    private Long shaftCompanyId(Shaft shaft) {
        return projectRepository.findById(shaft.getProjectId()).map(Project::getCompanyId).orElse(null);
    }
}
