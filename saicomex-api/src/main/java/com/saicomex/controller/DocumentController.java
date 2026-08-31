package com.saicomex.controller;

import com.saicomex.dto.DocumentDtos.DocumentSummary;
import com.saicomex.dto.DocumentDtos.DownloadUrl;
import com.saicomex.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * SRS §35 — {@code /api/documents}.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentSummary upload(
            @RequestParam String entityType,
            @RequestParam Long entityId,
            @RequestParam(required = false) String documentType,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam("file") MultipartFile file) {
        return documentService.upload(entityType, entityId, documentType, title, description, expiryDate, file);
    }

    @GetMapping
    public List<DocumentSummary> list(@RequestParam String entityType, @RequestParam Long entityId) {
        return documentService.list(entityType, entityId);
    }

    @GetMapping("/{id}/url")
    public DownloadUrl url(@PathVariable Long id) {
        return new DownloadUrl(documentService.downloadUrl(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        documentService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
