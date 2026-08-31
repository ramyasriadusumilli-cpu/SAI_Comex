package com.saicomex.controller;

import com.saicomex.dto.PageResponse;
import com.saicomex.dto.ProjectDtos.ProjectDetail;
import com.saicomex.dto.ProjectDtos.ProjectRequest;
import com.saicomex.dto.ProjectDtos.ProjectSummary;
import com.saicomex.service.ProjectService;
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
 * SRS §43 — {@code /api/projects}.
 *
 * <p>Controllers stay thin on purpose: bind, delegate, return. Permission and
 * scope checks live in the service so they cannot be bypassed by a second
 * caller reaching the same service from elsewhere.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public PageResponse<ProjectSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return projectService.list(status, type, search, pageable);
    }

    /** Unpaged list for dropdowns and the hierarchy tree. */
    @GetMapping("/options")
    public List<ProjectSummary> options() {
        return projectService.listAll();
    }

    @GetMapping("/{id}")
    public ProjectDetail get(@PathVariable Long id) {
        return projectService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetail create(@Valid @RequestBody ProjectRequest request) {
        return projectService.create(request);
    }

    @PutMapping("/{id}")
    public ProjectDetail update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(required = false) String reason) {
        projectService.delete(id, reason);
        return ResponseEntity.noContent().build();
    }
}
