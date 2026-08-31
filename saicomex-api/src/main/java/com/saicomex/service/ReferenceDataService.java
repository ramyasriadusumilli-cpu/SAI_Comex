package com.saicomex.service;

import com.saicomex.dto.ReferenceDtos.AgreementRuleTypeDto;
import com.saicomex.dto.ReferenceDtos.ContractTypeDto;
import com.saicomex.dto.ReferenceDtos.CurrencyDto;
import com.saicomex.dto.ReferenceDtos.ExpenseCategoryDto;
import com.saicomex.dto.ReferenceDtos.ProductionUnitDto;
import com.saicomex.dto.ReferenceDtos.ReferenceData;
import com.saicomex.dto.ReferenceDtos.ReportDefinitionDto;
import com.saicomex.dto.ReferenceDtos.RoleOption;
import com.saicomex.repository.AgreementRuleTypeRepository;
import com.saicomex.repository.ContractTypeRepository;
import com.saicomex.repository.CurrencyRepository;
import com.saicomex.repository.ExpenseCategoryRepository;
import com.saicomex.repository.ProductionUnitRepository;
import com.saicomex.repository.ReportDefinitionRepository;
import com.saicomex.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only lookup data for the SPA's dropdowns. No permission check on
 * purpose — every authenticated user needs currencies and statuses to render
 * a form, regardless of what they are otherwise allowed to do.
 *
 * <p>Status lists are duplicated here as constants rather than imported from
 * each aggregate's service — those {@code Set<String>} fields are private and
 * deliberately not shared, so a status added there is a two-line change here,
 * not a coupling between modules that are supposed to stay independent.
 */
@Service
@RequiredArgsConstructor
public class ReferenceDataService {

    private static final List<String> PROJECT_STATUSES = List.of(
            "PROPOSED", "PLANNING", "PROSPECTING", "DEVELOPMENT",
            "ACTIVE", "SUSPENDED", "COMPLETED", "CLOSED");

    private static final List<String> SHAFT_STATUSES = List.of(
            "PROPOSED", "CONTRACT_PENDING", "CONTRACTED", "MOBILISATION", "DEVELOPMENT",
            "ACTIVE", "TEMPORARILY_STOPPED", "SUSPENDED", "CLOSED");

    private static final List<String> OPERATION_STATUSES = List.of(
            "PROPOSED", "DEVELOPMENT", "ACTIVE", "SUSPENDED", "CLOSED");

    private static final List<String> CONTRACT_STATUSES = List.of(
            "DRAFT", "PENDING_APPROVAL", "APPROVED", "ACTIVE", "EXPIRED", "TERMINATED", "SUPERSEDED");

    private static final List<String> OPERATION_TYPES = List.of(
            "SHAFT_MINING", "ALLUVIAL", "RIVER", "PROCESSING", "MILLING", "OTHER");

    private final CurrencyRepository currencyRepository;
    private final ProductionUnitRepository productionUnitRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ContractTypeRepository contractTypeRepository;
    private final AgreementRuleTypeRepository agreementRuleTypeRepository;
    private final RoleRepository roleRepository;
    private final ReportDefinitionRepository reportDefinitionRepository;

    @Cacheable("referenceData")
    @Transactional(readOnly = true)
    public ReferenceData getAll() {
        return new ReferenceData(
                currencies(),
                productionUnits(),
                expenseCategoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc().stream()
                        .map(c -> new ExpenseCategoryDto(c.getId(), c.getCode(), c.getName(), c.getParentId(), c.getExpenseClass()))
                        .toList(),
                contractTypeRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc().stream()
                        .map(t -> new ContractTypeDto(t.getId(), t.getCode(), t.getName()))
                        .toList(),
                agreementRuleTypeRepository.findAllByIsActiveTrueOrderByDefaultSequenceAsc().stream()
                        .map(t -> new AgreementRuleTypeDto(t.getCode(), t.getName(), t.getStage(), t.getDefaultSequence()))
                        .toList(),
                roleRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc().stream()
                        .map(r -> new RoleOption(r.getId(), r.getCode(), r.getName()))
                        .toList(),
                PROJECT_STATUSES, SHAFT_STATUSES, OPERATION_STATUSES, CONTRACT_STATUSES, OPERATION_TYPES,
                reportDefinitionRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc().stream()
                        .map(d -> new ReportDefinitionDto(d.getId(), d.getCode(), d.getName(), d.getReportGroup(),
                                d.getDescription(), d.getSupportsPdf(), d.getSupportsExcel(), d.getSupportsCsv(),
                                d.getDefaultPeriod()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<CurrencyDto> currencies() {
        return currencyRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(c -> new CurrencyDto(c.getCode(), c.getName(), c.getSymbol(), c.getDecimalPlaces()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductionUnitDto> productionUnits() {
        return productionUnitRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc().stream()
                .map(u -> new ProductionUnitDto(u.getCode(), u.getName(), u.getUnitClass(), u.getBaseFactor(), u.getDecimalPlaces()))
                .toList();
    }
}
