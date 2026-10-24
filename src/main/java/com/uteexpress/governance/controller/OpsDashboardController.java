package com.uteexpress.governance.controller;

import com.uteexpress.governance.dto.OpsDashboardMetrics;
import com.uteexpress.governance.service.OpsDashboardService;
import com.uteexpress.governance.service.OpsDashboardReadService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class OpsDashboardController {
    private final OpsDashboardService dashboards;
    private final OpsDashboardReadService reports;
    public OpsDashboardController(OpsDashboardService dashboards, OpsDashboardReadService reports) {
        this.dashboards = dashboards; this.reports = reports;
    }
    @GetMapping("/admin/dashboard")
    public String admin(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long shopId, HttpServletRequest request, Model model) {
        OpsReportParameters.require(request, Set.of("from", "to", "shopId"));
        model.addAttribute("dashboard", dashboards.adminDashboard());
        model.addAttribute("metrics", reports.admin(from, to, shopId));
        return "governance/dashboard";
    }
    @GetMapping("/manager/dashboard")
    public String manager(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long shopId, HttpServletRequest request, Model model) {
        OpsReportParameters.require(request, Set.of("from", "to", "shopId"));
        model.addAttribute("dashboard", dashboards.managerDashboard());
        model.addAttribute("metrics", reports.manager(from, to, shopId));
        return "governance/dashboard";
    }
    @GetMapping("/admin/dashboard/metrics") @ResponseBody
    public ResponseEntity<OpsDashboardMetrics> adminMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long shopId, HttpServletRequest request) {
        OpsReportParameters.require(request, Set.of("from", "to", "shopId"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.admin(from, to, shopId));
    }
    @GetMapping("/manager/dashboard/metrics") @ResponseBody
    public ResponseEntity<OpsDashboardMetrics> managerMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long shopId, HttpServletRequest request) {
        OpsReportParameters.require(request, Set.of("from", "to", "shopId"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.manager(from, to, shopId));
    }
}
