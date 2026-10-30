package com.uteexpress.identity.controller;

import com.uteexpress.identity.dto.ResendOtpForm;
import com.uteexpress.identity.dto.VerifyOtpForm;
import com.uteexpress.identity.service.EmailVerificationResult;
import com.uteexpress.identity.service.EmailVerificationService;
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
public class EmailVerificationController {
    private static final String VIEW = "auth/verify-otp";
    private static final String INVALID_MESSAGE = "Mã xác minh không hợp lệ hoặc đã hết hạn.";
    private static final String RESEND_MESSAGE =
            "Nếu tài khoản hợp lệ và cần xác minh, mã xác minh sẽ được gửi đến email đã đăng ký.";

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @InitBinder("verifyOtpForm")
    void restrictVerificationFields(WebDataBinder binder) {
        binder.setAllowedFields("identifier", "code");
    }

    @InitBinder("resendOtpForm")
    void restrictResendFields(WebDataBinder binder) {
        binder.setAllowedFields("identifier");
    }

    @GetMapping("/verify-otp")
    String showVerification(
            @RequestParam(name = "identifier", defaultValue = "") String identifier,
            @RequestParam(name = "deliveryFailed", defaultValue = "false") boolean deliveryFailed,
            Model model) {
        if (!model.containsAttribute("verifyOtpForm")) {
            VerifyOtpForm form = new VerifyOtpForm();
            form.setIdentifier(identifier);
            model.addAttribute("verifyOtpForm", form);
        }
        if (!model.containsAttribute("resendOtpForm")) {
            ResendOtpForm resend = new ResendOtpForm();
            resend.setIdentifier(identifier);
            model.addAttribute("resendOtpForm", resend);
        }
        if (deliveryFailed) {
            model.addAttribute("errorMessage",
                    "Tài khoản đã được tạo nhưng chưa thể gửi email. Vui lòng thử gửi lại.");
        }
        return VIEW;
    }

    @PostMapping("/verify-otp")
    String verify(@Valid @ModelAttribute("verifyOtpForm") VerifyOtpForm form,
            BindingResult bindingResult, Model model) {
        addResendForm(model, form.getIdentifier());
        if (bindingResult.hasErrors()) {
            return VIEW;
        }
        EmailVerificationResult result = emailVerificationService.verify(
                form.getIdentifier(), form.getCode());
        if (result != EmailVerificationResult.VERIFIED) {
            model.addAttribute("errorMessage", INVALID_MESSAGE);
            return VIEW;
        }
        return "redirect:/login?verified=true";
    }

    @PostMapping("/verify-otp/resend")
    String resend(@Valid @ModelAttribute("resendOtpForm") ResendOtpForm form,
            BindingResult bindingResult, Model model) {
        addVerificationForm(model, form.getIdentifier());
        if (bindingResult.hasErrors()) {
            return VIEW;
        }
        emailVerificationService.sendVerificationCode(form.getIdentifier());
        model.addAttribute("successMessage", RESEND_MESSAGE);
        return VIEW;
    }

    private static void addResendForm(Model model, String identifier) {
        ResendOtpForm resend = new ResendOtpForm();
        resend.setIdentifier(identifier);
        model.addAttribute("resendOtpForm", resend);
    }

    private static void addVerificationForm(Model model, String identifier) {
        VerifyOtpForm verification = new VerifyOtpForm();
        verification.setIdentifier(identifier);
        model.addAttribute("verifyOtpForm", verification);
    }
}
