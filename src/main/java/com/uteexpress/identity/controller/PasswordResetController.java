package com.uteexpress.identity.controller;

import com.uteexpress.identity.dto.ForgotPasswordForm;
import com.uteexpress.identity.dto.ResetPasswordForm;
import com.uteexpress.identity.service.PasswordResetResult;
import com.uteexpress.identity.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {
    private static final String FORGOT_VIEW = "auth/forgot-password";
    private static final String RESET_VIEW = "auth/reset-password";
    private static final String GENERIC_ISSUANCE_MESSAGE =
            "Ở bước tiếp theo, nếu tài khoản hợp lệ, mã xác nhận sẽ được gửi đến email.";
    private static final String GENERIC_RESET_FAILURE =
            "Mã xác nhận không hợp lệ hoặc đã hết hạn.";

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @InitBinder("forgotPasswordForm")
    void restrictForgotFields(WebDataBinder binder) {
        binder.setAllowedFields("email");
    }

    @InitBinder("resetPasswordForm")
    void restrictResetFields(WebDataBinder binder) {
        binder.setAllowedFields("email", "code", "newPassword", "confirmPassword");
    }

    @GetMapping("/forgot-password")
    String showForgotPassword(Model model) {
        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        }
        return FORGOT_VIEW;
    }

    @PostMapping("/forgot-password")
    String requestReset(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return FORGOT_VIEW;
        }
        passwordResetService.sendResetCode(form.getEmail());
        return "redirect:/reset-password?requested=true";
    }

    @GetMapping("/reset-password")
    String showResetPassword(
            @RequestParam(name = "requested", defaultValue = "false") boolean requested,
            Model model) {
        if (!model.containsAttribute("resetPasswordForm")) {
            model.addAttribute("resetPasswordForm", new ResetPasswordForm());
        }
        if (requested) {
            model.addAttribute("successMessage", GENERIC_ISSUANCE_MESSAGE);
        }
        return RESET_VIEW;
    }

    @PostMapping("/reset-password")
    String resetPassword(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return RESET_VIEW;
        }
        PasswordResetResult result = passwordResetService.resetPassword(
                form.getEmail(), form.getCode(), form.getNewPassword());
        if (result != PasswordResetResult.RESET) {
            model.addAttribute("errorMessage", GENERIC_RESET_FAILURE);
            return RESET_VIEW;
        }
        return "redirect:/login?passwordReset=true";
    }
}
