package com.uteexpress.governance.controller;

import com.uteexpress.governance.service.RoleGovernanceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ActiveShipperController {
    private final RoleGovernanceService roles;

    public ActiveShipperController(RoleGovernanceService roles) { this.roles = roles; }

    @GetMapping("/{ops:admin|manager}/shippers")
    String list(@PathVariable String ops, Model model) {
        model.addAttribute("ops", ops);
        model.addAttribute("shippers", roles.activeShippers());
        return "governance/shippers/list";
    }
}
