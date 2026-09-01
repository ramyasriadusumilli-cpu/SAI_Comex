package com.saicomex.service;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.SupplierDtos.SupplierDetail;
import com.saicomex.dto.SupplierDtos.SupplierOption;
import com.saicomex.dto.SupplierDtos.SupplierRequest;
import com.saicomex.entity.Company;
import com.saicomex.entity.Supplier;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** SRS §19 — supplier master data. */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<SupplierDetail> list(String status, String type, String search, Pageable pageable) {
        permissions.require("suppliers.view");
        Page<Supplier> page = supplierRepository.search(blank(status), blank(type), blank(search), pageable);
        return PageResponse.of(page, SupplierService::toDetail);
    }

    @Transactional(readOnly = true)
    public List<SupplierOption> options() {
        permissions.require("suppliers.view");
        return supplierRepository.findByDeletedAtIsNullOrderByName().stream()
                .map(s -> new SupplierOption(s.getId(), s.getCode(), s.getName(), s.getSupplierType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierDetail get(Long id) {
        permissions.require("suppliers.view");
        return toDetail(supplier(id));
    }

    @Transactional
    public SupplierDetail create(SupplierRequest req) {
        permissions.require("suppliers.create");
        if (supplierRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(req.code())) {
            throw new BusinessRuleException("A supplier with code " + req.code() + " already exists");
        }
        Supplier s = new Supplier();
        s.setCompanyId(defaultCompanyId());
        apply(s, req);
        Supplier saved = supplierRepository.save(s);
        audit.record("CREATE", "SUPPLIER", saved.getId(), saved.getCode(), "Supplier " + saved.getCode() + " created");
        return toDetail(saved);
    }

    @Transactional
    public SupplierDetail update(Long id, SupplierRequest req) {
        permissions.require("suppliers.edit");
        Supplier s = supplier(id);
        if (!s.getCode().equalsIgnoreCase(req.code())
                && supplierRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(req.code())) {
            throw new BusinessRuleException("A supplier with code " + req.code() + " already exists");
        }
        apply(s, req);
        return toDetail(supplierRepository.save(s));
    }

    private void apply(Supplier s, SupplierRequest req) {
        s.setCode(req.code());
        s.setName(req.name());
        s.setSupplierType(req.supplierType());
        s.setContactPerson(req.contactPerson());
        s.setPhone(req.phone());
        s.setEmail(req.email());
        s.setAddress(req.address());
        s.setTaxNumber(req.taxNumber());
        s.setPaymentTerms(req.paymentTerms());
        s.setDefaultCurrency(req.defaultCurrency());
        s.setStatus(req.status() != null ? req.status() : "ACTIVE");
        s.setNotes(req.notes());
    }

    private Supplier supplier(Long id) {
        return supplierRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Supplier", id));
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private static String blank(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private static SupplierDetail toDetail(Supplier s) {
        return new SupplierDetail(s.getId(), s.getCode(), s.getName(), s.getSupplierType(), s.getContactPerson(),
                s.getPhone(), s.getEmail(), s.getAddress(), s.getTaxNumber(), s.getPaymentTerms(),
                s.getDefaultCurrency(), s.getStatus(), s.getNotes());
    }
}
