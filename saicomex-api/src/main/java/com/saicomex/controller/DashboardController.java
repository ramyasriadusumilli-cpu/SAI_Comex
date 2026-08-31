package com.saicomex.controller;

import com.saicomex.dto.DashboardDtos.*;
import com.saicomex.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * SRS §5, §45 — {@code /api/dashboard}.
 *
 * <p>One endpoint per drill-down level, each taking the level above as a
 * parameter. SRS §45 makes the drill-down mandatory: total expenses → project
 * → shaft → category → individual transaction. The route names follow that
 * path literally so it is obvious from the API which level a screen is on.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** Level 1 — group KPIs. */
    @GetMapping("/executive")
    public ExecutiveDashboard executive(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.executive(from, to);
    }

    @GetMapping("/status-counts")
    public StatusCounts statusCounts() {
        return dashboardService.statusCounts();
    }

    /** Level 2 — one row per project. */
    @GetMapping("/projects")
    public List<ProjectPerformance> projects(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.projectPerformance(from, to);
    }

    /** Level 3 — one row per shaft; also the SRS §30 comparison table. */
    @GetMapping("/shafts")
    public List<ShaftPerformance> shafts(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.shaftPerformance(projectId, from, to);
    }

    @GetMapping("/shafts/{shaftId}")
    public ShaftKpis shaftKpis(
            @PathVariable Long shaftId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.shaftKpis(shaftId, from, to);
    }

    /**
     * Level 4 — expenditure inside one shaft, by category. Level 5 (the
     * individual transactions) is {@code GET /api/expenses?shaftId=&categoryId=},
     * which already exists and already enforces its own permissions.
     */
    @GetMapping("/shafts/{shaftId}/expenses")
    public List<CategoryBreakdown> shaftExpenses(
            @PathVariable Long shaftId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.expenseBreakdown(shaftId, from, to);
    }
}
