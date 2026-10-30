package com.uteexpress.governance.controller;

import com.uteexpress.governance.dto.AdminRevenueSummary;
import com.uteexpress.governance.service.AdminRevenueService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminRevenueController {
    private final AdminRevenueService revenue;
    public AdminRevenueController(AdminRevenueService revenue) { this.revenue=revenue; }
    @GetMapping("/admin/dashboard/revenue")
    public String page(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toExclusive,
            @RequestParam(required=false) Long shopId, HttpServletRequest request, Model model) {
        model.addAttribute("revenue",data(from,toExclusive,shopId,request).getBody());return "governance/revenue";
    }
    @GetMapping("/admin/dashboard/revenue/metrics") @ResponseBody
    public ResponseEntity<AdminRevenueSummary> data(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toExclusive,
            @RequestParam(required=false) Long shopId, HttpServletRequest request) {
        OpsReportParameters.require(request,Set.of("from","toExclusive","shopId"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(revenue.dashboard(from,toExclusive,shopId));
    }
}
