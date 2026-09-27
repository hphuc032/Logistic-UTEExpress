package com.uteexpress.shop.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.shop.service.ShopApprovalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/shops")
public class ShopApprovalController {
    private final ShopApprovalService approvals;

    public ShopApprovalController(ShopApprovalService approvals) { this.approvals = approvals; }

    @GetMapping
    String pending(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("shops", approvals.pending(page));
        return "governance/shops/list";
    }

    @GetMapping("/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAttribute("shop", approvals.get(id));
        return "governance/shops/detail";
    }

    @PostMapping("/{id}/approve")
    String approve(@PathVariable Long id, @RequestParam Long version, RedirectAttributes redirect) {
        try {
            approvals.approve(id, version);
            redirect.addFlashAttribute("successMessage", "Đã duyệt cửa hàng. Chủ cửa hàng cần đăng nhập lại.");
        } catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT) throw exception;
            redirect.addFlashAttribute("errorMessage", "Đơn đã thay đổi hoặc tài khoản chưa hoạt động. Hãy tải lại.");
        }
        return "redirect:/admin/shops/" + id;
    }

    @PostMapping("/{id}/reject")
    String reject(@PathVariable Long id, @RequestParam Long version,
            @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        try {
            approvals.reject(id, version, reason);
            redirect.addFlashAttribute("successMessage", "Đã từ chối đơn đăng ký cửa hàng.");
        } catch (ApplicationException exception) {
            if (exception.errorCode() != ErrorCode.CONFLICT
                    && exception.errorCode() != ErrorCode.VALIDATION_FAILED) throw exception;
            redirect.addFlashAttribute("errorMessage", exception.errorCode() == ErrorCode.CONFLICT
                    ? "Đơn đã thay đổi. Hãy tải lại trước khi quyết định."
                    : "Vui lòng nhập lý do từ chối (tối đa 1000 ký tự).");
        }
        return "redirect:/admin/shops/" + id;
    }
}
