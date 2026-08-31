package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.AgreementDtos.AgreementDetail;
import com.saicomex.dto.AgreementDtos.AgreementRequest;
import com.saicomex.dto.AgreementDtos.AgreementRuleDetail;
import com.saicomex.dto.AgreementDtos.AgreementRuleRequest;
import com.saicomex.dto.AgreementDtos.AgreementRuleTierDto;
import com.saicomex.dto.AgreementDtos.AgreementRuleTierRequest;
import com.saicomex.dto.AgreementDtos.AgreementSummary;
import com.saicomex.dto.AgreementDtos.RuleTypeOption;
import com.saicomex.entity.AgreementRule;
import com.saicomex.entity.AgreementRuleTier;
import com.saicomex.entity.CommercialAgreement;
import com.saicomex.entity.Contract;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.AgreementRuleRepository;
import com.saicomex.repository.AgreementRuleTierRepository;
import com.saicomex.repository.AgreementRuleTypeRepository;
import com.saicomex.repository.CommercialAgreementRepository;
import com.saicomex.repository.ContractRepository;
import com.saicomex.repository.PartnerRepository;
import com.saicomex.repository.ShaftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * SRS §11 — commercial agreements and their rules.
 *
 * <p>Every check here exists because the calculation engine
 * ({@link com.saicomex.engine.CommercialCalculationEngine}) has no recourse
 * once a settlement is running: a bad rule set must be refused at save or
 * activation time, with a message an operator can act on, not discovered as
 * an exception mid-settlement.
 */
@Service
@RequiredArgsConstructor
public class CommercialAgreementService {

    private static final Set<String> ALLOCATION_RULE_TYPES =
            Set.of("REVENUE_SHARE", "PRODUCTION_SHARE", "PROFIT_SHARE");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CommercialAgreementRepository agreementRepository;
    private final AgreementRuleRepository ruleRepository;
    private final AgreementRuleTierRepository tierRepository;
    private final AgreementRuleTypeRepository ruleTypeRepository;
    private final ContractRepository contractRepository;
    private final ShaftRepository shaftRepository;
    private final PartnerRepository partnerRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<AgreementSummary> listForContract(Long contractId) {
        permissions.require("agreements.view");
        return agreementRepository.findAllByContractIdAndDeletedAtIsNullOrderByEffectiveFromDesc(contractId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgreementDetail get(Long id) {
        permissions.require("agreements.view");
        return toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public List<RuleTypeOption> ruleTypes() {
        permissions.require("agreements.view");
        return ruleTypeRepository.findAllByIsActiveTrueOrderByDefaultSequenceAsc().stream()
                .map(t -> new RuleTypeOption(t.getCode(), t.getName(), t.getDescription(), t.getStage(), t.getDefaultSequence()))
                .toList();
    }

    @Transactional
    public AgreementDetail create(AgreementRequest req) {
        permissions.require("agreements.create");
        contractRepository.findByIdAndDeletedAtIsNull(req.contractId())
                .orElseThrow(() -> NotFoundException.of("Contract", req.contractId()));
        validateHeader(req);
        validateRules(req.rules());

        CommercialAgreement agreement = new CommercialAgreement();
        apply(agreement, req);
        agreement.setStatus("DRAFT");
        CommercialAgreement saved = agreementRepository.save(agreement);

        saveRules(saved.getId(), req.rules());

        audit.record("CREATE", "AGREEMENT", saved.getId(), saved.getName(),
                "Agreement " + saved.getName() + " created for contract " + req.contractId());
        return toDetail(saved);
    }

    /**
     * Replaces the whole rule set. Refused outright on an ACTIVE agreement —
     * the operator must create a new agreement version instead, so a rule
     * change is never made invisibly under settlements already computed
     * against the old set.
     */
    @Transactional
    public AgreementDetail update(Long id, AgreementRequest req) {
        permissions.require("agreements.edit");
        CommercialAgreement agreement = load(id);
        if ("ACTIVE".equals(agreement.getStatus())) {
            throw new BusinessRuleException(
                    "This agreement is ACTIVE — its rules cannot be edited in place. Create a new agreement "
                    + "version instead and activate it, which supersedes this one.");
        }
        validateHeader(req);
        validateRules(req.rules());

        apply(agreement, req);
        CommercialAgreement saved = agreementRepository.save(agreement);

        for (AgreementRule existingRule : ruleRepository.findAllByAgreementIdOrderBySequenceNoAsc(id)) {
            tierRepository.deleteAllByRuleId(existingRule.getId());
        }
        ruleRepository.deleteAllByAgreementId(id);
        saveRules(id, req.rules());

        audit.record("UPDATE", "AGREEMENT", id, saved.getName(), "Agreement rule set replaced");
        return toDetail(saved);
    }

    /**
     * Supersedes any currently ACTIVE agreement on the same contract before
     * activating this one, so exactly one agreement governs a contract at any
     * instant — mirroring the one-ACTIVE-contract-per-shaft rule.
     */
    @Transactional
    public AgreementDetail activate(Long id) {
        permissions.require("agreements.approve");
        CommercialAgreement agreement = load(id);
        if ("ACTIVE".equals(agreement.getStatus())) {
            throw new BusinessRuleException("This agreement is already active");
        }

        List<AgreementRule> rules = ruleRepository.findAllByAgreementIdOrderBySequenceNoAsc(id);
        requireActivatable(agreement, rules);

        agreementRepository.findByContractIdAndStatusAndDeletedAtIsNull(agreement.getContractId(), "ACTIVE")
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    other.setStatus("SUPERSEDED");
                    other.setEffectiveTo(agreement.getEffectiveFrom().minusDays(1));
                    agreementRepository.save(other);
                    audit.record("SUPERSEDE", "AGREEMENT", other.getId(), other.getName(),
                            "Superseded by agreement " + agreement.getId());
                });

        agreement.setStatus("ACTIVE");
        agreement.setApprovedBy(AuditContext.currentUser());
        agreement.setApprovedAt(LocalDateTime.now());
        CommercialAgreement saved = agreementRepository.save(agreement);

        audit.record("ACTIVATE", "AGREEMENT", id, saved.getName(), "Agreement activated");
        return toDetail(saved);
    }

    // ---------------------------------------------------------------- helpers

    private CommercialAgreement load(Long id) {
        return agreementRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Agreement", id));
    }

    private void validateHeader(AgreementRequest req) {
        if (req.effectiveTo() != null && req.effectiveFrom().isAfter(req.effectiveTo())) {
            throw new BusinessRuleException("Effective-from date cannot be after the effective-to date");
        }
        if (req.defaultSaicomexPercent() != null && req.defaultPartnerPercent() != null
                && !sumsToHundred(req.defaultSaicomexPercent(), req.defaultPartnerPercent())) {
            throw new BusinessRuleException(
                    "The default split totals " + req.defaultSaicomexPercent().add(req.defaultPartnerPercent())
                    + "%, not 100%");
        }
    }

    private void validateRules(List<AgreementRuleRequest> rules) {
        if (rules == null) return;
        for (AgreementRuleRequest r : rules) {
            if (r.effectiveTo() != null && r.effectiveFrom() != null && r.effectiveFrom().isAfter(r.effectiveTo())) {
                throw new BusinessRuleException(
                        "Rule '" + r.name() + "': effective-from date cannot be after the effective-to date");
            }
            if ("PERCENTAGE".equals(r.calculationMethod()) && r.saicomexPercent() != null && r.partnerPercent() != null
                    && !sumsToHundred(r.saicomexPercent(), r.partnerPercent())) {
                throw new BusinessRuleException(
                        "Rule '" + r.name() + "' splits to " + r.saicomexPercent().add(r.partnerPercent())
                        + "%, not 100%");
            }
            if ("TIERED".equals(r.calculationMethod())) {
                validateTiers(r.name(), r.tiers());
            }
        }
    }

    /**
     * A TIERED rule must have at least one tier, tiers must not overlap, and
     * the top tier must be open-ended — otherwise a value above the top tier
     * has no rule and the engine throws at settlement time (SRS §12). Checked
     * here, at save, rather than discovered then.
     */
    private void validateTiers(String ruleName, List<AgreementRuleTierRequest> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new BusinessRuleException("Rule '" + ruleName + "' is TIERED but has no tiers defined");
        }
        List<AgreementRuleTierRequest> sorted = tiers.stream()
                .sorted(Comparator.comparing(AgreementRuleTierRequest::fromValue))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            AgreementRuleTierRequest prev = sorted.get(i - 1);
            AgreementRuleTierRequest cur = sorted.get(i);
            if (prev.toValue() == null || cur.fromValue().compareTo(prev.toValue()) < 0) {
                throw new BusinessRuleException(
                        "Rule '" + ruleName + "' has overlapping tiers around " + cur.fromValue()
                        + " — each tier's 'from' must equal or exceed the previous tier's 'to'");
            }
        }
        AgreementRuleTierRequest top = sorted.get(sorted.size() - 1);
        if (top.toValue() != null) {
            throw new BusinessRuleException(
                    "Rule '" + ruleName + "' must end with an open-ended top tier (leave 'to' blank on the "
                    + "highest tier) — otherwise a value above " + top.toValue() + " has no rule and settlement fails");
        }
    }

    /**
     * An agreement must have at least one allocation rule or both default
     * percentages set before it can be activated — otherwise the engine has
     * nothing to split the pool with (SRS §12).
     */
    private void requireActivatable(CommercialAgreement agreement, List<AgreementRule> rules) {
        boolean hasAllocationRule = rules.stream().anyMatch(r -> ALLOCATION_RULE_TYPES.contains(r.getRuleType()));
        boolean hasDefaults = agreement.getDefaultSaicomexPercent() != null && agreement.getDefaultPartnerPercent() != null;
        if (!hasAllocationRule && !hasDefaults) {
            throw new BusinessRuleException(
                    "This agreement has no allocation rule (REVENUE_SHARE / PRODUCTION_SHARE / PROFIT_SHARE) and "
                    + "no default split. Add one or set the default percentages before activating.");
        }
        for (AgreementRule rule : rules) {
            if (!"TIERED".equals(rule.getCalculationMethod())) continue;
            List<AgreementRuleTier> tiers = tierRepository.findAllByRuleIdOrderByTierNoAsc(rule.getId());
            if (tiers.isEmpty()) {
                throw new BusinessRuleException("Rule '" + rule.getName() + "' is TIERED but has no tiers defined");
            }
            AgreementRuleTier top = tiers.get(tiers.size() - 1);
            if (top.getToValue() != null) {
                throw new BusinessRuleException(
                        "Rule '" + rule.getName() + "' must end with an open-ended top tier before this "
                        + "agreement can be activated");
            }
        }
    }

    private void saveRules(Long agreementId, List<AgreementRuleRequest> rules) {
        if (rules == null) return;
        for (AgreementRuleRequest r : rules) {
            AgreementRule rule = new AgreementRule();
            rule.setAgreementId(agreementId);
            rule.setRuleType(r.ruleType());
            rule.setName(r.name());
            rule.setDescription(r.description());
            rule.setSequenceNo(r.sequenceNo() == null ? 100 : r.sequenceNo());
            rule.setScope(r.scope() == null ? "ALL" : r.scope());
            rule.setExpenseCategoryId(r.expenseCategoryId());
            rule.setScopeValue(r.scopeValue());
            rule.setCalculationMethod(r.calculationMethod());
            rule.setSaicomexPercent(r.saicomexPercent());
            rule.setPartnerPercent(r.partnerPercent());
            rule.setFixedAmount(r.fixedAmount());
            rule.setRateAmount(r.rateAmount());
            rule.setRateUnit(r.rateUnit());
            rule.setCurrency(r.currency());
            rule.setBorneBy(r.borneBy() == null ? "SHARED" : r.borneBy());
            rule.setDeductBeforeSplit(r.deductBeforeSplit() != null && r.deductBeforeSplit());
            rule.setMinAmount(r.minAmount());
            rule.setMaxAmount(r.maxAmount());
            rule.setCapPercent(r.capPercent());
            rule.setRecoverableTotal(r.recoverableTotal());
            rule.setEffectiveFrom(r.effectiveFrom());
            rule.setEffectiveTo(r.effectiveTo());
            rule.setIsActive(r.isActive() == null || r.isActive());
            rule.setNotes(r.notes());
            AgreementRule savedRule = ruleRepository.save(rule);

            if (r.tiers() != null) {
                for (AgreementRuleTierRequest t : r.tiers()) {
                    AgreementRuleTier tier = new AgreementRuleTier();
                    tier.setRuleId(savedRule.getId());
                    tier.setTierNo(t.tierNo());
                    tier.setFromValue(t.fromValue());
                    tier.setToValue(t.toValue());
                    tier.setSaicomexPercent(t.saicomexPercent());
                    tier.setPartnerPercent(t.partnerPercent());
                    tier.setFixedAmount(t.fixedAmount());
                    tier.setRateAmount(t.rateAmount());
                    tierRepository.save(tier);
                }
            }
        }
    }

    private void apply(CommercialAgreement a, AgreementRequest r) {
        a.setContractId(r.contractId());
        a.setName(r.name());
        a.setDescription(r.description());
        a.setEffectiveFrom(r.effectiveFrom());
        a.setEffectiveTo(r.effectiveTo());
        a.setSettlementBasis(r.settlementBasis() == null ? "NET_REVENUE" : r.settlementBasis());
        a.setDefaultSaicomexPercent(r.defaultSaicomexPercent());
        a.setDefaultPartnerPercent(r.defaultPartnerPercent());
        a.setCurrency(r.currency() == null ? "USD" : r.currency());
        a.setRoundingScale(r.roundingScale() == null ? Short.valueOf((short) 2) : r.roundingScale());
        a.setRoundingMode(r.roundingMode() == null ? "HALF_UP" : r.roundingMode());
        a.setNotes(r.notes());
    }

    private AgreementSummary toSummary(CommercialAgreement a) {
        int ruleCount = ruleRepository.findAllByAgreementIdOrderBySequenceNoAsc(a.getId()).size();
        return new AgreementSummary(a.getId(), a.getContractId(), a.getName(), a.getStatus(),
                a.getEffectiveFrom(), a.getEffectiveTo(), a.getSettlementBasis(), a.getCurrency(), ruleCount);
    }

    private AgreementDetail toDetail(CommercialAgreement a) {
        List<AgreementRuleDetail> rules = ruleRepository.findAllByAgreementIdOrderBySequenceNoAsc(a.getId()).stream()
                .map(this::toRuleDetail)
                .toList();

        Contract contract = contractRepository.findByIdAndDeletedAtIsNull(a.getContractId()).orElse(null);
        String shaftName = contract == null || contract.getShaftId() == null ? null
                : shaftRepository.findByIdAndDeletedAtIsNull(contract.getShaftId()).map(s -> s.getName()).orElse(null);
        String partnerName = contract == null ? null
                : partnerRepository.findByIdAndDeletedAtIsNull(contract.getPartnerId()).map(p -> p.getLegalName()).orElse(null);

        return com.saicomex.dto.AgreementDtos.toDetail(a, contract == null ? null : contract.getContractNumber(),
                shaftName, partnerName, rules);
    }

    private AgreementRuleDetail toRuleDetail(AgreementRule r) {
        List<AgreementRuleTierDto> tiers = tierRepository.findAllByRuleIdOrderByTierNoAsc(r.getId()).stream()
                .map(t -> new AgreementRuleTierDto(t.getId(), t.getTierNo(), t.getFromValue(), t.getToValue(),
                        t.getSaicomexPercent(), t.getPartnerPercent(), t.getFixedAmount(), t.getRateAmount()))
                .toList();
        return new AgreementRuleDetail(r.getId(), r.getRuleType(), r.getName(), r.getDescription(), r.getSequenceNo(),
                r.getScope(), r.getExpenseCategoryId(), r.getScopeValue(), r.getCalculationMethod(),
                r.getSaicomexPercent(), r.getPartnerPercent(), r.getFixedAmount(), r.getRateAmount(),
                r.getRateUnit(), r.getCurrency(), r.getBorneBy(), r.getDeductBeforeSplit(),
                r.getMinAmount(), r.getMaxAmount(), r.getCapPercent(), r.getRecoverableTotal(), r.getRecoveredToDate(),
                r.getEffectiveFrom(), r.getEffectiveTo(), r.getIsActive(), r.getNotes(), tiers);
    }

    private static boolean sumsToHundred(BigDecimal a, BigDecimal b) {
        return a.add(b).setScale(6, RoundingMode.HALF_UP).compareTo(HUNDRED) == 0;
    }
}
