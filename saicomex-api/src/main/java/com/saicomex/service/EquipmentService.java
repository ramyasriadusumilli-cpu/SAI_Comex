package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.EquipmentDtos.AllocationDetail;
import com.saicomex.dto.EquipmentDtos.AllocationRequest;
import com.saicomex.dto.EquipmentDtos.EquipmentDetail;
import com.saicomex.dto.EquipmentDtos.EquipmentRequest;
import com.saicomex.dto.EquipmentDtos.EquipmentSummary;
import com.saicomex.dto.PageResponse;
import com.saicomex.entity.Company;
import com.saicomex.entity.Equipment;
import com.saicomex.entity.EquipmentAllocation;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.equipment.EquipmentPolicy;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.EquipmentAllocationRepository;
import com.saicomex.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * SRS §20-21 — the asset register and its allocation history. The invariant:
 * {@code equipment}'s placement columns always mirror the single open
 * {@code equipment_allocations} row. {@link #allocate} is the only way placement
 * changes — it closes the current allocation and opens a new one, never
 * overwriting placement without recording where the machine has been.
 */
@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentAllocationRepository allocationRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<EquipmentSummary> list(String status, String type, Long shaftId, String search, Pageable pageable) {
        permissions.require("equipment.view");
        Page<Equipment> page = equipmentRepository.search(blank(status), blank(type), shaftId, blank(search), pageable);
        return PageResponse.of(page, EquipmentService::toSummary);
    }

    @Transactional(readOnly = true)
    public EquipmentDetail get(Long id) {
        permissions.require("equipment.view");
        return toDetail(equipment(id));
    }

    @Transactional
    public EquipmentDetail create(EquipmentRequest req) {
        permissions.require("equipment.create");
        if (equipmentRepository.existsByAssetNumberIgnoreCaseAndDeletedAtIsNull(req.assetNumber())) {
            throw new BusinessRuleException("Equipment with asset number " + req.assetNumber() + " already exists");
        }
        Equipment e = new Equipment();
        e.setCompanyId(defaultCompanyId());
        apply(e, req);
        Equipment saved = equipmentRepository.save(e);

        // If it is created already placed, open its first allocation so the
        // history is complete from day one.
        if (req.projectId() != null) {
            EquipmentAllocation a = new EquipmentAllocation();
            a.setEquipmentId(saved.getId());
            a.setProjectId(req.projectId());
            a.setMiningOperationId(req.miningOperationId());
            a.setShaftId(req.shaftId());
            a.setFromDate(req.purchaseDate() != null ? req.purchaseDate() : LocalDate.now());
            a.setOperatorEmployeeId(req.operatorEmployeeId());
            a.setOpeningHours(saved.getOperatingHours());
            a.setReason("Initial placement");
            allocationRepository.save(a);
        }

        audit.record("CREATE", "EQUIPMENT", saved.getId(), saved.getAssetNumber(),
                "Equipment " + saved.getAssetNumber() + " (" + saved.getName() + ") registered");
        return toDetail(saved);
    }

    @Transactional
    public EquipmentDetail update(Long id, EquipmentRequest req) {
        permissions.require("equipment.edit");
        Equipment e = equipment(id);
        if (!e.getAssetNumber().equalsIgnoreCase(req.assetNumber())
                && equipmentRepository.existsByAssetNumberIgnoreCaseAndDeletedAtIsNull(req.assetNumber())) {
            throw new BusinessRuleException("Equipment with asset number " + req.assetNumber() + " already exists");
        }
        apply(e, req);
        return toDetail(equipmentRepository.save(e));
    }

    /**
     * Re-allocate equipment to a new placement. Closes the current open
     * allocation (stamping its end date and closing hours), opens a new one, and
     * mirrors the new placement onto the equipment row.
     */
    @Transactional
    public EquipmentDetail allocate(Long id, AllocationRequest req) {
        permissions.require("equipment.edit");
        Equipment e = equipment(id);

        allocationRepository.findByEquipmentIdAndToDateIsNull(id).ifPresent(current -> {
            EquipmentPolicy.validateReallocation(current.getFromDate(), req.fromDate());
            current.setToDate(req.fromDate());
            if (current.getClosingHours() == null) {
                current.setClosingHours(req.openingHours() != null ? req.openingHours() : e.getOperatingHours());
            }
            EquipmentPolicy.validateHours(current.getOpeningHours(), current.getClosingHours());
            // Flush the close before opening the new row: the unique index allows
            // only one open (to_date IS NULL) allocation per equipment.
            allocationRepository.saveAndFlush(current);
        });

        EquipmentAllocation a = new EquipmentAllocation();
        a.setEquipmentId(id);
        a.setProjectId(req.projectId());
        a.setMiningOperationId(req.miningOperationId());
        a.setShaftId(req.shaftId());
        a.setFromDate(req.fromDate());
        a.setOperatorEmployeeId(req.operatorEmployeeId());
        a.setOpeningHours(req.openingHours() != null ? req.openingHours() : e.getOperatingHours());
        a.setHireRate(req.hireRate());
        a.setHireRateUnit(req.hireRateUnit());
        a.setRateCurrency(req.rateCurrency());
        a.setReason(req.reason());
        allocationRepository.save(a);

        // Mirror the new current placement onto the equipment row.
        e.setProjectId(req.projectId());
        e.setMiningOperationId(req.miningOperationId());
        e.setShaftId(req.shaftId());
        e.setOperatorEmployeeId(req.operatorEmployeeId());
        equipmentRepository.save(e);

        audit.record("ALLOCATE", "EQUIPMENT", id, e.getAssetNumber(),
                "Equipment " + e.getAssetNumber() + " allocated to project " + req.projectId()
                        + (req.shaftId() != null ? ", shaft " + req.shaftId() : "") + " from " + req.fromDate());
        return toDetail(e);
    }

    @Transactional(readOnly = true)
    public List<AllocationDetail> allocationHistory(Long id) {
        permissions.require("equipment.view");
        equipment(id);
        return allocationRepository.findByEquipmentIdOrderByFromDateDescIdDesc(id).stream()
                .map(EquipmentService::toAllocationDetail).toList();
    }

    @Transactional
    public EquipmentDetail setStatus(Long id, String status, String reason) {
        permissions.require("equipment.edit");
        Equipment e = equipment(id);
        String old = e.getStatus();
        e.setStatus(status);
        equipmentRepository.save(e);
        audit.record("STATUS", "EQUIPMENT", id, e.getAssetNumber(),
                "Status " + old + " -> " + status + (reason != null ? " (" + reason + ")" : ""));
        return toDetail(e);
    }

    // ---------------------------------------------------------------- helpers

    private void apply(Equipment e, EquipmentRequest req) {
        e.setAssetNumber(req.assetNumber());
        e.setName(req.name());
        e.setEquipmentType(req.equipmentType());
        e.setDescription(req.description());
        e.setManufacturer(req.manufacturer());
        e.setModel(req.model());
        e.setSerialNumber(req.serialNumber());
        e.setRegistrationNumber(req.registrationNumber());
        e.setYearOfManufacture(req.yearOfManufacture());
        e.setPurchaseDate(req.purchaseDate());
        e.setPurchaseCost(req.purchaseCost());
        e.setPurchaseCurrency(req.purchaseCurrency());
        e.setCurrentValue(req.currentValue());
        e.setOwnership(req.ownership() != null ? req.ownership() : "OWNED");
        e.setOwnerPartnerId(req.ownerPartnerId());
        e.setSupplierId(req.supplierId());
        e.setProjectId(req.projectId());
        e.setMiningOperationId(req.miningOperationId());
        e.setShaftId(req.shaftId());
        e.setOperatorEmployeeId(req.operatorEmployeeId());
        if (req.operatingHours() != null) e.setOperatingHours(req.operatingHours());
        e.setServiceIntervalHours(req.serviceIntervalHours());
        e.setNextServiceDate(req.nextServiceDate());
        e.setInsuranceExpiry(req.insuranceExpiry());
        e.setLicenceExpiry(req.licenceExpiry());
        if (req.status() != null) e.setStatus(req.status());
        e.setNotes(req.notes());
    }

    private Equipment equipment(Long id) {
        return equipmentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Equipment", id));
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private static String blank(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private static EquipmentSummary toSummary(Equipment e) {
        return new EquipmentSummary(e.getId(), e.getAssetNumber(), e.getName(), e.getEquipmentType(),
                e.getStatus(), e.getShaftId(), e.getOperatingHours());
    }

    private static AllocationDetail toAllocationDetail(EquipmentAllocation a) {
        return new AllocationDetail(a.getId(), a.getProjectId(), a.getMiningOperationId(), a.getShaftId(),
                a.getFromDate(), a.getToDate(), a.getOperatorEmployeeId(), a.getOpeningHours(), a.getClosingHours(),
                a.getHireRate(), a.getHireRateUnit(), a.getReason(), a.getCreatedBy());
    }

    private static EquipmentDetail toDetail(Equipment e) {
        return new EquipmentDetail(e.getId(), e.getAssetNumber(), e.getName(), e.getEquipmentType(), e.getDescription(),
                e.getManufacturer(), e.getModel(), e.getSerialNumber(), e.getRegistrationNumber(), e.getYearOfManufacture(),
                e.getPurchaseDate(), e.getPurchaseCost(), e.getPurchaseCurrency(), e.getCurrentValue(), e.getOwnership(),
                e.getOwnerPartnerId(), e.getSupplierId(), e.getProjectId(), e.getMiningOperationId(), e.getShaftId(),
                e.getOperatorEmployeeId(), e.getOperatingHours(), e.getServiceIntervalHours(), e.getNextServiceDate(),
                e.getInsuranceExpiry(), e.getLicenceExpiry(), e.getStatus(), e.getNotes());
    }
}
