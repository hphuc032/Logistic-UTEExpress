package com.uteexpress.governance.controller;

import com.uteexpress.governance.service.CommissionPolicyManagementService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Instant;

@Controller
@RequestMapping("/{ops:admin|manager}/commissions")
public class CommissionController {
    private final CommissionPolicyManagementService policies;

    public CommissionController(CommissionPolicyManagementService policies) {
        this.policies = policies;
    }

    @GetMapping
    public String list(@PathVariable String ops, Model model) {
        model.addAttribute("policies", policies.list());
        model.addAttribute("ops", ops);
        return "governance/commissions/list";
    }

    @PostMapping
    public String create(@PathVariable String ops, @RequestParam BigDecimal ratePercent,
            @RequestParam Instant effectiveFrom, RedirectAttributes redirect) {
        policies.create(ratePercent, effectiveFrom);
        redirect.addFlashAttribute("successMessage", "Đã tạo chính sách hoa hồng.");
        return "redirect:/" + ops + "/commissions";
    }
}
