package com.saicomex.service;

import com.saicomex.dto.MaintenanceDtos.MaintenanceDetail;
import com.saicomex.dto.MaintenanceDtos.MaintenanceRequest;
import com.saicomex.dto.MaintenanceDtos.MaintenanceSummary;
import com.saicomex.dto.MaintenanceDtos.PartDetail;
import com.saicomex.dto.MaintenanceDtos.PartRequest;
import com.saicomex.dto.PageResponse;
import com.saicomex.entity.Company;
import com.saicomex.entity.MaintenancePart;
import com.saicomex.entity.MaintenanceRecord;
import com.saicomex.equipment.EquipmentPolicy;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.EquipmentRepository;
import com.saicomex.repository.MaintenancePartRepository;
import com.saicomex.repository.MaintenanceRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * SRS §22 — maintenance jobs and their parts. A job's total cost rolls up from
 * the part lines plus labour and other, computed through the pure
 * {@link EquipmentPolicy}. Full audit + soft delete via {@link MaintenanceRecord}.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private static final List<String> EDITABLE = List.of("OPEN", "IN_PROGRESS", "AWAITING_PARTS");

    private final MaintenanceRecordRepository recordRepository;
    private final MaintenancePartRepository partRepository;
    private final EquipmentRepository equipmentRepository;
    private final CompanyRepository companyRepository;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<MaintenanceSummary> list(String status, String type, Long equipmentId,
                                                 LocalDate from, LocalDate to, Pageable pageable) {
        permissions.require("maintenance.view");
        Page<MaintenanceRecord> page = recordRepository.search(blank(status), blank(type), equipmentId, from, to, pageable);
        return PageResponse.of(page, MaintenanceService::toSummary);
    }

    @Transactional(readOnly = true)
    public MaintenanceDetail get(Long id) {
        permissions.require("maintenance.view");
        return toDetail(record(id));
    }

    @Transactional
    public MaintenanceDetail create(MaintenanceRequest req) {
        permissions.require("maintenance.create");
        equipmentRepository.findByIdAndDeletedAtIsNull(req.equipmentId())
                .orElseThrow(() -> NotFoundException.of("Equipment", req.equipmentId()));

        MaintenanceRecord m = new MaintenanceRecord();
        m.setCompanyId(defaultCompanyId());
        m.setJobNumber(nextJobNumber());
        m.setStatus("OPEN");
        applyHeader(m, req);
        MaintenanceRecord saved = recordRepository.save(m);

        BigDecimal partsCost = writeParts(saved.getId(), req.parts());
        saveCosts(saved, partsCost, req.labourCost(), req.otherCost());

        audit.record("CREATE", "MAINTENANCE", saved.getId(), saved.getJobNumber(),
                "Maintenance " + saved.getJobNumber() + " opened on equipment " + saved.getEquipmentId());
        return toDetail(saved);
    }

    @Transactional
    public MaintenanceDetail update(Long id, MaintenanceRequest req) {
        permissions.require("maintenance.edit");
        MaintenanceRecord m = record(id);
        if (!EDITABLE.contains(m.getStatus())) {
            throw new BusinessRuleException("A " + m.getStatus() + " maintenance job can no longer be edited");
        }
        applyHeader(m, req);
        partRepository.deleteAll(partRepository.findByMaintenanceRecordId(id));
        BigDecimal partsCost = writeParts(id, req.parts());
        saveCosts(m, partsCost, req.labourCost(), req.otherCost());
        return toDetail(m);
    }

    @Transactional
    public MaintenanceDetail setStatus(Long id, String status, String reason) {
        permissions.require("maintenance.edit");
        MaintenanceRecord m = record(id);
        String old = m.getStatus();
        m.setStatus(status);
        if ("COMPLETED".equals(status) && m.getCompletedDate() == null) {
            m.setCompletedDate(LocalDate.now());
        }
        recordRepository.save(m);
        audit.record("STATUS", "MAINTENANCE", id, m.getJobNumber(),
                "Maintenance " + m.getJobNumber() + " " + old + " -> " + status + (reason != null ? " (" + reason + ")" : ""));
        return toDetail(m);
    }

    // ---------------------------------------------------------------- helpers

    private void applyHeader(MaintenanceRecord m, MaintenanceRequest req) {
        m.setEquipmentId(req.equipmentId());
        m.setMaintenanceType(req.maintenanceType());
        m.setPriority(req.priority() != null ? req.priority() : "NORMAL");
        m.setReportedDate(req.reportedDate());
        m.setServiceDate(req.serviceDate());
        m.setHourMeterReading(req.hourMeterReading());
        m.setDescription(req.description());
        m.setWorkPerformed(req.workPerformed());
        m.setTechnicianName(req.technicianName());
        m.setTechnicianEmployeeId(req.technicianEmployeeId());
        m.setSupplierId(req.supplierId());
        m.setCurrency(req.currency());
        if (req.downtimeHours() != null) m.setDowntimeHours(req.downtimeHours());
        m.setProjectId(req.projectId());
        m.setShaftId(req.shaftId());
        m.setNextServiceDate(req.nextServiceDate());
        m.setNotes(req.notes());
    }

    private BigDecimal writeParts(Long recordId, List<PartRequest> parts) {
        List<BigDecimal> totals = new ArrayList<>();
        if (parts != null) {
            for (PartRequest pr : parts) {
                MaintenancePart p = new MaintenancePart();
                p.setMaintenanceRecordId(recordId);
                p.setItemId(pr.itemId());
                p.setDescription(pr.description());
                p.setQuantity(pr.quantity());
                p.setUnitCost(pr.unitCost());
                BigDecimal total = pr.quantity().multiply(pr.unitCost() != null ? pr.unitCost() : BigDecimal.ZERO)
                        .setScale(4, RoundingMode.HALF_UP);
                p.setTotalCost(total);
                partRepository.save(p);
                totals.add(total);
            }
        }
        return EquipmentPolicy.partsCost(totals);
    }

    private void saveCosts(MaintenanceRecord m, BigDecimal partsCost, BigDecimal labour, BigDecimal other) {
        m.setPartsCost(partsCost);
        m.setLabourCost(labour != null ? labour : BigDecimal.ZERO);
        m.setOtherCost(other != null ? other : BigDecimal.ZERO);
        m.setTotalCost(EquipmentPolicy.maintenanceTotal(m.getPartsCost(), m.getLabourCost(), m.getOtherCost()));
        recordRepository.save(m);
    }

    private MaintenanceRecord record(Long id) {
        return recordRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("MaintenanceRecord", id));
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream().findFirst().map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }

    private String nextJobNumber() {
        String prefix = "JOB-" + LocalDate.now().getYear() + "-";
        long count = recordRepository.count() + 1;
        String candidate = prefix + String.format("%05d", count);
        while (recordRepository.existsByJobNumberIgnoreCaseAndDeletedAtIsNull(candidate)) {
            count++;
            candidate = prefix + String.format("%05d", count);
        }
        return candidate;
    }

    private static String blank(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private static MaintenanceSummary toSummary(MaintenanceRecord m) {
        return new MaintenanceSummary(m.getId(), m.getJobNumber(), m.getEquipmentId(), m.getMaintenanceType(),
                m.getPriority(), m.getServiceDate(), m.getTotalCost(), m.getStatus());
    }

    private MaintenanceDetail toDetail(MaintenanceRecord m) {
        List<PartDetail> parts = partRepository.findByMaintenanceRecordId(m.getId()).stream()
                .map(p -> new PartDetail(p.getId(), p.getItemId(), p.getDescription(), p.getQuantity(),
                        p.getUnitCost(), p.getTotalCost()))
                .toList();
        return new MaintenanceDetail(m.getId(), m.getJobNumber(), m.getEquipmentId(), m.getMaintenanceType(),
                m.getPriority(), m.getReportedDate(), m.getServiceDate(), m.getCompletedDate(), m.getNextServiceDate(),
                m.getHourMeterReading(), m.getDescription(), m.getWorkPerformed(), m.getTechnicianName(),
                m.getSupplierId(), m.getPartsCost(), m.getLabourCost(), m.getOtherCost(), m.getTotalCost(),
                m.getCurrency(), m.getExpenseId(), m.getDowntimeHours(), m.getProjectId(), m.getShaftId(),
                m.getStatus(), m.getNotes(), parts);
    }
}
