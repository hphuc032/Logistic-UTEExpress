package com.uteexpress.governance.controller;

import com.uteexpress.governance.service.OpsDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OpsDashboardController {
    private final OpsDashboardService dashboards;

    public OpsDashboardController(OpsDashboardService dashboards) { this.dashboards = dashboards; }

    @GetMapping("/admin/dashboard")
    public String admin(Model model) {
        model.addAttribute("dashboard", dashboards.adminDashboard());
        return "governance/dashboard";
    }

    @GetMapping("/manager/dashboard")
    public String manager(Model model) {
        model.addAttribute("dashboard", dashboards.managerDashboard());
        return "governance/dashboard";
    }
}
