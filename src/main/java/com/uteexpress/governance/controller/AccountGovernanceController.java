package com.uteexpress.governance.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.service.AccountGovernanceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/{ops:admin|manager}/accounts")
public class AccountGovernanceController {
    private final AccountGovernanceService accounts;

    public AccountGovernanceController(AccountGovernanceService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    String list(@PathVariable String ops, @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("ops", ops);
        model.addAttribute("query", query);
        model.addAttribute("accounts", accounts.search(query, page));
        return "governance/accounts/list";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable String ops, @PathVariable Long id, Model model) {
        model.addAttribute("ops", ops);
        model.addAttribute("account", accounts.get(id));
        return "governance/accounts/detail";
    }

    @PostMapping("/{id}/{action:lock|unlock}")
    String setLocked(@PathVariable String ops, @PathVariable Long id, @PathVariable String action,
            @RequestParam Long version, RedirectAttributes redirect) {
        if (!"admin".equals(ops)) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        try {
            accounts.setLocked(id, version, "lock".equals(action));
            redirect.addFlashAttribute("successMessage", "Đã cập nhật trạng thái tài khoản.");
        } catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT) throw exception;
            redirect.addFlashAttribute("errorMessage", "Tài khoản đã thay đổi hoặc không thể cập nhật. Hãy kiểm tra lại.");
        }
        return "redirect:/admin/accounts/" + id;
    }
}
