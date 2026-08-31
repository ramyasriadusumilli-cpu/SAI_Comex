package com.saicomex.dto;

import com.saicomex.entity.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SRS §35. Response shapes for the polymorphic document attachment. There is
 * no request record — {@code POST /api/documents} is multipart, so the
 * controller binds individual form fields rather than a JSON body.
 */
public final class DocumentDtos {

    private DocumentDtos() {}

    public record DocumentSummary(
            Long id,
            String entityType,
            Long entityId,
            String documentType,
            String title,
            String description,
            String fileName,
            String contentType,
            Long fileSizeBytes,
            Boolean isConfidential,
            LocalDate expiryDate,
            Long uploadedByUserId,
            LocalDateTime createdAt,
            String createdBy
    ) {}

    public record DownloadUrl(String url) {}

    public static DocumentSummary toSummary(Document d) {
        return new DocumentSummary(
                d.getId(), d.getEntityType(), d.getEntityId(), d.getDocumentType(),
                d.getTitle(), d.getDescription(), d.getFileName(), d.getContentType(), d.getFileSizeBytes(),
                d.getIsConfidential(), d.getExpiryDate(), d.getUploadedByUserId(),
                d.getCreatedAt(), d.getCreatedBy());
    }
}
