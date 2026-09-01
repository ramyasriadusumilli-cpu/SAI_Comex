package com.saicomex.dto;

import jakarta.validation.constraints.NotBlank;

/** Request/response records for suppliers (SRS §19). */
public final class SupplierDtos {

    private SupplierDtos() {
    }

    public record SupplierRequest(
            @NotBlank String code,
            @NotBlank String name,
            String supplierType,
            String contactPerson,
            String phone,
            String email,
            String address,
            String taxNumber,
            String paymentTerms,
            String defaultCurrency,
            String status,
            String notes
    ) {}

    public record SupplierOption(Long id, String code, String name, String supplierType) {}

    public record SupplierDetail(
            Long id,
            String code,
            String name,
            String supplierType,
            String contactPerson,
            String phone,
            String email,
            String address,
            String taxNumber,
            String paymentTerms,
            String defaultCurrency,
            String status,
            String notes
    ) {}
}
