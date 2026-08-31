package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.PageResponse;
import com.saicomex.dto.PartnerDtos.PartnerContractRef;
import com.saicomex.dto.PartnerDtos.PartnerDetail;
import com.saicomex.dto.PartnerDtos.PartnerRequest;
import com.saicomex.dto.PartnerDtos.PartnerShaftRef;
import com.saicomex.dto.PartnerDtos.PartnerSummary;
import com.saicomex.entity.Company;
import com.saicomex.entity.Contract;
import com.saicomex.entity.Partner;
import com.saicomex.entity.Project;
import com.saicomex.entity.Shaft;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.ContractRepository;
import com.saicomex.repository.PartnerRepository;
import com.saicomex.repository.PaymentRepository;
import com.saicomex.repository.ProjectRepository;
import com.saicomex.repository.SettlementRepository;
import com.saicomex.repository.ShaftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * SRS §9 — partners / shaft owners.
 *
 * <p>Banking and payment details are restricted data: every read path decides
 * disclosure here, via {@code permissions.has("partners.banking")}, never in
 * the controller or the client.
 */
@Service
@RequiredArgsConstructor
public class PartnerService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "BLACKLISTED");

    private final PartnerRepository partnerRepository;
    private final ShaftRepository shaftRepository;
    private final ContractRepository contractRepository;
    private final ProjectRepository projectRepository;
    private final SettlementRepository settlementRepository;
    private final PaymentRepository paymentRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<PartnerSummary> list(String status, String search, Pageable pageable) {
        permissions.require("partners.view");
        Page<Partner> page = partnerRepository.search(blankToNull(status), blankToNull(search), pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public List<PartnerSummary> listAll() {
        permissions.require("partners.view");
        return partnerRepository.findAllByDeletedAtIsNullOrderByLegalNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnerDetail get(Long id) {
        permissions.require("partners.view");
        return toDetail(load(id));
    }

    @Transactional
    public PartnerDetail create(PartnerRequest req) {
        permissions.require("partners.create");
        validate(req, null);

        Partner partner = new Partner();
        partner.setCompanyId(defaultCompanyId());
        apply(partner, req);
        partner.setStatus(req.status() == null ? "ACTIVE" : req.status());
        Partner saved = partnerRepository.save(partner);

        audit.record("CREATE", "PARTNER", saved.getId(), saved.getCode(),
                "Partner " + saved.getLegalName() + " created");
        return toDetail(saved);
    }

    @Transactional
    public PartnerDetail update(Long id, PartnerRequest req) {
        permissions.require("partners.edit");
        Partner partner = load(id);
        validate(req, partner);

        audit.recordChange("PARTNER", id, partner.getCode(), "legalName", partner.getLegalName(), req.legalName(), null);
        audit.recordChange("PARTNER", id, partner.getCode(), "status", partner.getStatus(), req.status(), null);

        apply(partner, req);
        if (req.status() != null) partner.setStatus(req.status());
        return toDetail(partnerRepository.save(partner));
    }

    /**
     * SRS §39 — soft delete only. A partner still holding any non-deleted
     * contract is refused: the contract's financial history points at this
     * partner and must not be left dangling.
     */
    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("partners.delete");
        Partner partner = load(id);

        long contracts = contractRepository.findAllByPartnerIdAndDeletedAtIsNullOrderByEffectiveDateDesc(id).size();
        if (contracts > 0) {
            throw new BusinessRuleException(
                    "This partner still holds " + contracts + " contract(s). Terminate or reassign them before "
                    + "deleting the partner record.");
        }

        partner.softDelete(AuditContext.currentUser());
        partnerRepository.save(partner);
        audit.record("DELETE", "PARTNER", id, partner.getCode(),
                "Partner archived" + (reason == null ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- helpers

    private Partner load(Long id) {
        return partnerRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Partner", id));
    }

    private void validate(PartnerRequest req, Partner existing) {
        if (req.status() != null && !STATUSES.contains(req.status())) {
            throw new BusinessRuleException("Unknown partner status: " + req.status());
        }
        boolean codeChanged = existing == null || !req.code().equalsIgnoreCase(existing.getCode());
        if (codeChanged && partnerRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(req.code())) {
            throw new BusinessRuleException("Partner code " + req.code() + " is already in use");
        }
    }

    private void apply(Partner p, PartnerRequest r) {
        p.setCode(r.code());
        p.setLegalName(r.legalName());
        p.setTradingName(r.tradingName());
        p.setPartnerType(r.partnerType() == null ? "COMPANY" : r.partnerType());
        p.setRegistrationNumber(r.registrationNumber());
        p.setTaxNumber(r.taxNumber());
        p.setIdNumber(r.idNumber());
        p.setContactPerson(r.contactPerson());
        p.setPhone(r.phone());
        p.setAlternatePhone(r.alternatePhone());
        p.setEmail(r.email());
        p.setAddress(r.address());
        p.setCity(r.city());
        p.setCountry(r.country());
        p.setBankName(r.bankName());
        p.setBankBranch(r.bankBranch());
        p.setBankAccountName(r.bankAccountName());
        p.setBankAccountNumber(r.bankAccountNumber());
        p.setBankSwift(r.bankSwift());
        p.setPaymentCurrency(r.paymentCurrency());
        p.setPaymentMethod(r.paymentMethod());
        p.setOnboardedDate(r.onboardedDate());
        p.setNotes(r.notes());
    }

    private PartnerSummary toSummary(Partner p) {
        int shaftCount = shaftRepository.findAllByOwnerPartnerIdAndDeletedAtIsNull(p.getId()).size();
        BigDecimal totalPaid = nz(paymentRepository.sumPaidByPartner(p.getId()));
        BigDecimal outstanding = nz(settlementRepository.sumOutstandingByPartner(p.getId()));
        return new PartnerSummary(
                p.getId(), p.getCode(), p.getLegalName(), p.getTradingName(), p.getPartnerType(),
                p.getContactPerson(), p.getPhone(), p.getEmail(), p.getStatus(),
                shaftCount, totalPaid.add(outstanding), totalPaid, outstanding);
    }

    private PartnerDetail toDetail(Partner p) {
        List<PartnerShaftRef> shafts = shaftRepository.findAllByOwnerPartnerIdAndDeletedAtIsNull(p.getId()).stream()
                .map(this::toShaftRef)
                .toList();
        List<PartnerContractRef> contracts = contractRepository
                .findAllByPartnerIdAndDeletedAtIsNullOrderByEffectiveDateDesc(p.getId()).stream()
                .map(this::toContractRef)
                .toList();
        BigDecimal totalPaid = nz(paymentRepository.sumPaidByPartner(p.getId()));
        BigDecimal outstanding = nz(settlementRepository.sumOutstandingByPartner(p.getId()));

        boolean includeBanking = permissions.has("partners.banking");
        return com.saicomex.dto.PartnerDtos.toDetail(p, shafts, contracts, totalPaid, outstanding, includeBanking);
    }

    private PartnerShaftRef toShaftRef(Shaft s) {
        String projectName = projectRepository.findByIdAndDeletedAtIsNull(s.getProjectId())
                .map(Project::getName).orElse(null);
        return new PartnerShaftRef(s.getId(), s.getCode(), s.getName(), projectName, s.getStatus());
    }

    private PartnerContractRef toContractRef(Contract c) {
        String shaftName = c.getShaftId() == null ? null
                : shaftRepository.findByIdAndDeletedAtIsNull(c.getShaftId()).map(Shaft::getName).orElse(null);
        return new PartnerContractRef(c.getId(), c.getContractNumber(), shaftName, c.getStatus(),
                c.getEffectiveDate(), c.getExpiryDate());
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream()
                .findFirst()
                .map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
