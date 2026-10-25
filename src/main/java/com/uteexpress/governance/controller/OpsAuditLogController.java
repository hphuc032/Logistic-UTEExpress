package com.uteexpress.governance.controller;

import com.uteexpress.governance.dto.*;
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
@RequestMapping("/admin/audit-logs")
public class OpsAuditLogController {
    private final OpsDashboardReadService reports;
    public OpsAuditLogController(OpsDashboardReadService reports) { this.reports = reports; }
    @GetMapping
    public String page(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action, @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long actorId, @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "0") int page, HttpServletRequest request, Model model) {
        model.addAttribute("audit", read(from, to, action, targetType, actorId, targetId, page, request));
        return "governance/audit-logs";
    }
    @GetMapping("/data") @ResponseBody
    public ResponseEntity<OpsAuditPage> data(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String action, @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long actorId, @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "0") int page, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(read(from, to, action, targetType, actorId, targetId, page, request));
    }
    private OpsAuditPage read(LocalDate from, LocalDate to, String action, String type, Long actor, Long target,
            int page, HttpServletRequest request) {
        OpsReportParameters.require(request, Set.of("from", "to", "action", "targetType", "actorId", "targetId", "page"));
        return reports.audit(from, to, new OpsAuditFilter(action, type, actor, target, page));
    }
}
