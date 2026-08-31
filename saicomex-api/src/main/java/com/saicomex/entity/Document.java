package com.saicomex.entity;

import com.saicomex.common.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * SRS §35 — a polymorphic document attachment. Bytes live in MinIO; this row
 * holds the object key and metadata, keyed to any entity via
 * {@code entityType}/{@code entityId}.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** COMPANY | PROJECT | OPERATION | SHAFT | PARTNER | CONTRACT | AGREEMENT
     *  | EXPENSE | PURCHASE | SALE | EQUIPMENT | PRODUCTION | PAYMENT
     *  | SETTLEMENT | MAINTENANCE | EMPLOYEE | INVENTORY */
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** CONTRACT | LICENCE | INVOICE | RECEIPT | ASSAY | PHOTO | ID | OTHER */
    @Column(name = "document_type", length = 60)
    private String documentType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "supersedes_id")
    private Long supersedesId;

    @Column(name = "is_confidential", nullable = false)
    private Boolean isConfidential = false;

    /** Licences / permits / insurance. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;
}
