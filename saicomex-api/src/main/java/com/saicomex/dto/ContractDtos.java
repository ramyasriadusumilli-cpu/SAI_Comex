package com.saicomex.dto;

import com.saicomex.entity.Contract;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SRS §10. Request and response shapes for contracts, including the
 * versioning trail an amendment produces.
 */
public final class ContractDtos {

    private ContractDtos() {}

    /** Create / update payload. */
    public record ContractRequest(
            @NotBlank @Size(max = 50) String contractNumber,
            @NotNull Long projectId,
            Long miningOperationId,
            @NotNull Long shaftId,
            @NotNull Long partnerId,
            Long contractTypeId,
            @Size(max = 200) String title,
            @NotNull LocalDate effectiveDate,
            LocalDate expiryDate,
            LocalDate renewalDate,
            LocalDate signedDate,
            String status,
            @Size(max = 3) String settlementCurrency,
            String settlementFrequency,
            String specialConditions
    ) {}

    /** Row shape for the contracts list. */
    public record ContractSummary(
            Long id,
            String contractNumber,
            String projectName,
            String shaftName,
            String partnerName,
            String contractTypeName,
            String status,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            String settlementCurrency,
            boolean hasActiveAgreement
    ) {}

    /** One row of the contract's versioning trail (SRS §10). */
    public record ContractVersionDto(
            Long id,
            Integer versionNumber,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String changeReason,
            String changeSummary,
            String status,
            String approvedBy,
            LocalDateTime approvedAt
    ) {}

    /** One entry of the contract's approval history. */
    public record ApprovalEntry(
            Long id,
            Integer stepNo,
            String stepName,
            String requiredRole,
            String action,
            String actorEmail,
            String actorRole,
            String comments,
            LocalDateTime actedAt
    ) {}

    /** The commercial agreement(s) attached to this contract, summarised. */
    public record AgreementRef(
            Long id,
            String name,
            String status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String settlementBasis
    ) {}

    /** Payload for {@code POST /{id}/amend} — SRS §10 contract versioning. */
    public record AmendmentRequest(
            @NotNull LocalDate effectiveFrom,
            @NotBlank String changeReason,
            String changeSummary
    ) {}

    /** Payload for {@code POST /{id}/terminate}. */
    public record TerminateRequest(
            @NotBlank String reason
    ) {}

    /** Full record for the contract detail page. */
    public record ContractDetail(
            Long id,
            String contractNumber,
            Long projectId,
            String projectName,
            Long miningOperationId,
            Long shaftId,
            String shaftName,
            Long partnerId,
            String partnerName,
            Long contractTypeId,
            String contractTypeName,
            String title,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            LocalDate renewalDate,
            LocalDate signedDate,
            String status,
            Integer currentVersion,
            String settlementCurrency,
            String settlementFrequency,
            String specialConditions,
            String terminationNotes,
            String approvedBy,
            LocalDateTime approvedAt,
            List<ContractVersionDto> versions,
            List<AgreementRef> agreements,
            List<ApprovalEntry> approvalHistory,
            int documentCount,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime updatedAt,
            String updatedBy
    ) {}

    public static ContractDetail toDetail(Contract c, String projectName, String shaftName, String partnerName,
                                          String contractTypeName, List<ContractVersionDto> versions,
                                          List<AgreementRef> agreements, List<ApprovalEntry> approvalHistory,
                                          int documentCount) {
        return new ContractDetail(
                c.getId(), c.getContractNumber(), c.getProjectId(), projectName,
                c.getMiningOperationId(), c.getShaftId(), shaftName, c.getPartnerId(), partnerName,
                c.getContractTypeId(), contractTypeName, c.getTitle(),
                c.getEffectiveDate(), c.getExpiryDate(), c.getRenewalDate(), c.getSignedDate(),
                c.getStatus(), c.getCurrentVersion(), c.getSettlementCurrency(), c.getSettlementFrequency(),
                c.getSpecialConditions(), c.getTerminationNotes(), c.getApprovedBy(), c.getApprovedAt(),
                versions, agreements, approvalHistory, documentCount,
                c.getCreatedAt(), c.getCreatedBy(), c.getUpdatedAt(), c.getUpdatedBy());
    }
}
