package com.saicomex.service;

import com.saicomex.common.AuditContext;
import com.saicomex.dto.DocumentDtos;
import com.saicomex.dto.DocumentDtos.DocumentSummary;
import com.saicomex.entity.Company;
import com.saicomex.entity.Document;
import com.saicomex.entity.User;
import com.saicomex.exception.BusinessRuleException;
import com.saicomex.exception.NotFoundException;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * SRS §35 — the polymorphic document attachment: any entity, any file. Bytes
 * live in MinIO ({@link StorageService}); this service owns the metadata row.
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx", "mp4");

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final CompanyRepository companyRepository;
    private final StorageService storageService;
    private final PermissionService permissions;
    private final AuditService audit;

    @Transactional
    public DocumentSummary upload(String entityType, Long entityId, String documentType, String title,
                                  String description, LocalDate expiryDate, MultipartFile file) {
        permissions.require("documents.create");
        validateFile(file);
        User me = permissions.currentUser();

        String extension = extensionOf(file.getOriginalFilename());
        String sanitisedName = sanitiseFileName(file.getOriginalFilename());
        String objectKey = entityType + "/" + entityId + "/" + UUID.randomUUID() + "-" + sanitisedName;
        storageService.upload(file, objectKey);

        Document doc = new Document();
        doc.setCompanyId(defaultCompanyId());
        doc.setEntityType(entityType);
        doc.setEntityId(entityId);
        doc.setDocumentType(documentType);
        doc.setTitle(title);
        doc.setDescription(description);
        doc.setFileName(sanitisedName);
        doc.setStorageKey(objectKey);
        doc.setContentType(file.getContentType());
        doc.setFileSizeBytes(file.getSize());
        doc.setExpiryDate(expiryDate);
        doc.setUploadedByUserId(me.getId());
        Document saved = documentRepository.save(doc);

        audit.record("CREATE", "DOCUMENT", saved.getId(), saved.getTitle(),
                "Document \"" + saved.getTitle() + "\" (." + extension + ") uploaded for "
                + entityType + " " + entityId);
        return DocumentDtos.toSummary(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentSummary> list(String entityType, Long entityId) {
        permissions.require("documents.view");
        return documentRepository
                .findAllByEntityTypeAndEntityIdAndDeletedAtIsNullOrderByCreatedAtDesc(entityType, entityId)
                .stream()
                .map(DocumentDtos::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public String downloadUrl(Long id) {
        permissions.require("documents.view");
        Document doc = load(id);
        return storageService.presignedUrl(doc.getStorageKey());
    }

    /**
     * Soft delete only — the row is marked deleted, but the underlying object
     * is left in MinIO, consistent with SRS §39's "never physically delete"
     * rule: an accidental delete stays recoverable.
     */
    @Transactional
    public void delete(Long id, String reason) {
        permissions.require("documents.delete");
        Document doc = load(id);
        doc.softDelete(AuditContext.currentUser());
        documentRepository.save(doc);
        audit.record("DELETE", "DOCUMENT", id, doc.getTitle(),
                "Document deleted" + (reason == null || reason.isBlank() ? "" : " — " + reason));
    }

    // ---------------------------------------------------------------- helpers

    private Document load(Long id) {
        return documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> NotFoundException.of("Document", id));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("No file was uploaded");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessRuleException("File exceeds the 25 MB upload limit");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessRuleException(
                    "File type ." + extension + " is not allowed. Accepted types: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Strips any path and replaces anything but letters, digits, dot, dash, underscore. */
    private static String sanitiseFileName(String fileName) {
        String base = fileName == null ? "file" : fileName;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Long defaultCompanyId() {
        return companyRepository.findAll().stream()
                .findFirst()
                .map(Company::getId)
                .orElseThrow(() -> new BusinessRuleException("No company record exists — the database has not been seeded"));
    }
}
