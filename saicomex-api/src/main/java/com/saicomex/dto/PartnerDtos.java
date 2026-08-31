package com.saicomex.dto;

import com.saicomex.entity.Partner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SRS §9. Request and response shapes for partners / shaft owners.
 *
 * <p>Banking and payment fields are restricted: {@link #toDetail} takes an
 * {@code includeBanking} flag rather than deciding for itself, so the caller
 * (the service, after a {@code permissions.has("partners.banking")} check)
 * controls disclosure. The controller never sees the unredacted fields.
 */
public final class PartnerDtos {

    private PartnerDtos() {}

    /** Create / update payload. */
    public record PartnerRequest(
            @NotBlank @Size(max = 30)  String code,
            @NotBlank @Size(max = 200) String legalName,
            @Size(max = 200) String tradingName,
            @Size(max = 30)  String partnerType,
            @Size(max = 60)  String registrationNumber,
            @Size(max = 60)  String taxNumber,
            @Size(max = 60)  String idNumber,
            @Size(max = 160) String contactPerson,
            @Size(max = 40)  String phone,
            @Size(max = 40)  String alternatePhone,
            @Size(max = 160) String email,
            String address,
            @Size(max = 120) String city,
            @Size(max = 80)  String country,
            @Size(max = 160) String bankName,
            @Size(max = 120) String bankBranch,
            @Size(max = 160) String bankAccountName,
            @Size(max = 60)  String bankAccountNumber,
            @Size(max = 30)  String bankSwift,
            @Size(max = 3)   String paymentCurrency,
            @Size(max = 40)  String paymentMethod,
            String status,
            LocalDate onboardedDate,
            String notes
    ) {}

    /** Row shape for the partners list. */
    public record PartnerSummary(
            Long id,
            String code,
            String legalName,
            String tradingName,
            String partnerType,
            String contactPerson,
            String phone,
            String email,
            String status,
            int shaftCount,
            BigDecimal totalPayable,
            BigDecimal totalPaid,
            BigDecimal outstanding
    ) {}

    /** A shaft this partner owns, as shown on the partner detail page. */
    public record PartnerShaftRef(
            Long shaftId,
            String shaftCode,
            String shaftName,
            String projectName,
            String status
    ) {}

    /** A contract this partner holds, as shown on the partner detail page. */
    public record PartnerContractRef(
            Long contractId,
            String contractNumber,
            String shaftName,
            String status,
            LocalDate effectiveDate,
            LocalDate expiryDate
    ) {}

    /** Full record for the partner detail page. */
    public record PartnerDetail(
            Long id,
            String code,
            String legalName,
            String tradingName,
            String partnerType,
            String registrationNumber,
            String taxNumber,
            String idNumber,
            String contactPerson,
            String phone,
            String alternatePhone,
            String email,
            String address,
            String city,
            String country,
            String bankName,
            String bankBranch,
            String bankAccountName,
            String bankAccountNumber,
            String bankSwift,
            String paymentCurrency,
            String paymentMethod,
            String status,
            LocalDate onboardedDate,
            String notes,
            List<PartnerShaftRef> shafts,
            List<PartnerContractRef> contracts,
            BigDecimal totalPayable,
            BigDecimal totalPaid,
            BigDecimal outstanding,
            LocalDateTime createdAt,
            String createdBy,
            LocalDateTime updatedAt,
            String updatedBy
    ) {}

    public static PartnerDetail toDetail(Partner p, List<PartnerShaftRef> shafts, List<PartnerContractRef> contracts,
                                         BigDecimal totalPaid, BigDecimal outstanding, boolean includeBanking) {
        BigDecimal totalPayable = totalPaid.add(outstanding);
        return new PartnerDetail(
                p.getId(), p.getCode(), p.getLegalName(), p.getTradingName(), p.getPartnerType(),
                p.getRegistrationNumber(), p.getTaxNumber(), p.getIdNumber(),
                p.getContactPerson(), p.getPhone(), p.getAlternatePhone(), p.getEmail(),
                p.getAddress(), p.getCity(), p.getCountry(),
                includeBanking ? p.getBankName() : null,
                includeBanking ? p.getBankBranch() : null,
                includeBanking ? p.getBankAccountName() : null,
                includeBanking ? p.getBankAccountNumber() : null,
                includeBanking ? p.getBankSwift() : null,
                includeBanking ? p.getPaymentCurrency() : null,
                includeBanking ? p.getPaymentMethod() : null,
                p.getStatus(), p.getOnboardedDate(), p.getNotes(),
                shafts, contracts, totalPayable, totalPaid, outstanding,
                p.getCreatedAt(), p.getCreatedBy(), p.getUpdatedAt(), p.getUpdatedBy());
    }
}
