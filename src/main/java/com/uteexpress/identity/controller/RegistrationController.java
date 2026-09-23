package com.uteexpress.identity.controller;

import com.uteexpress.identity.dto.RegisterForm;
import com.uteexpress.identity.dto.RegistrationCommand;
import com.uteexpress.identity.service.EmailDispatchResult;
import com.uteexpress.identity.service.EmailVerificationService;
import com.uteexpress.identity.service.RegistrationConflictException;
import com.uteexpress.identity.service.RegistrationService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationController {
    private static final String REGISTER_VIEW = "auth/register";
    private static final String GENERIC_CONFLICT_MESSAGE =
            "Không thể tạo tài khoản với thông tin đã cung cấp.";

    private final RegistrationService registrationService;
    private final EmailVerificationService emailVerificationService;

    public RegistrationController(RegistrationService registrationService,
            EmailVerificationService emailVerificationService) {
        this.registrationService = registrationService;
        this.emailVerificationService = emailVerificationService;
    }

    @InitBinder("registerForm")
    void restrictRegistrationFields(WebDataBinder binder) {
        binder.setAllowedFields("email", "username", "password", "confirmPassword");
    }

    @GetMapping("/register")
    String showRegistration(@RequestParam(name = "registered", defaultValue = "false") boolean registered,
            Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm());
        }
        if (registered) {
            model.addAttribute("successMessage",
                    "Tài khoản đã được tạo. Bạn cần xác minh email trước khi đăng nhập.");
        }
        return REGISTER_VIEW;
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return REGISTER_VIEW;
        }

        try {
            var outcome = registrationService.register(new RegistrationCommand(
                    form.getEmail(), form.getUsername(), form.getPassword()));
            EmailDispatchResult dispatch = emailVerificationService
                    .sendVerificationCode(outcome.username());
            redirectAttributes.addAttribute("identifier", outcome.username());
            if (dispatch == EmailDispatchResult.DELIVERY_FAILED) {
                redirectAttributes.addAttribute("deliveryFailed", true);
            }
        } catch (RegistrationConflictException exception) {
            model.addAttribute("errorMessage", GENERIC_CONFLICT_MESSAGE);
            return REGISTER_VIEW;
        }
        return "redirect:/verify-otp";
    }
}
