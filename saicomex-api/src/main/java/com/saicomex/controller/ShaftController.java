package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.ShaftDtos.ShaftDetail;
import com.saicomex.dto.ShaftDtos.ShaftRequest;
import com.saicomex.dto.ShaftDtos.ShaftSummary;
import com.saicomex.dto.ShaftDtos.StatusUpdateRequest;
import com.saicomex.service.ShaftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SRS §8 — {@code /api/shafts}.
 */
@RestController
@RequestMapping("/api/shafts")
@RequiredArgsConstructor
public class ShaftController {

    private final ShaftService shaftService;

    @GetMapping
    public PageResponse<ShaftSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long operationId,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return shaftService.list(status, projectId, operationId, partnerId, search, pageable);
    }

    /** Unpaged list for dropdowns and the hierarchy tree. */
    @GetMapping("/options")
    public List<ShaftSummary> options(@RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) Long operationId) {
        return shaftService.options(projectId, operationId);
    }

    @GetMapping("/{id}")
    public ShaftDetail get(@PathVariable Long id) {
        return shaftService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShaftDetail create(@Valid @RequestBody ShaftRequest request) {
        return shaftService.create(request);
    }

    @PutMapping("/{id}")
    public ShaftDetail update(@PathVariable Long id, @Valid @RequestBody ShaftRequest request) {
        return shaftService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ShaftDetail updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return shaftService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        shaftService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
