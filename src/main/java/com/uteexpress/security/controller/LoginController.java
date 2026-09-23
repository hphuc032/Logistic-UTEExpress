package com.uteexpress.security.controller;

import com.uteexpress.security.RoleCode;
import com.uteexpress.security.dto.LoginCommand;
import com.uteexpress.security.dto.LoginForm;
import com.uteexpress.security.dto.LoginOutcome;
import com.uteexpress.security.service.LoginService;
import com.uteexpress.security.web.JwtCookieService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.AuthenticationException;
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
public class LoginController {
    private static final String LOGIN_VIEW = "auth/login";
    private static final String GENERIC_FAILURE_MESSAGE =
            "Không thể đăng nhập với thông tin đã cung cấp.";

    private final LoginService loginService;
    private final JwtCookieService cookies;

    public LoginController(LoginService loginService, JwtCookieService cookies) {
        this.loginService = loginService;
        this.cookies = cookies;
    }

    @InitBinder("loginForm")
    void restrictLoginFields(WebDataBinder binder) {
        binder.setAllowedFields("identifier", "password");
    }

    @GetMapping("/login")
    String showLogin(@RequestParam(name = "logout", defaultValue = "false") boolean logout,
            @RequestParam(name = "verified", defaultValue = "false") boolean verified,
            Model model) {
        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        if (logout) {
            model.addAttribute("successMessage", "Bạn đã đăng xuất an toàn.");
        } else if (verified) {
            model.addAttribute("successMessage",
                    "Xác minh email thành công. Bạn có thể đăng nhập.");
        }
        return LOGIN_VIEW;
    }

    @PostMapping("/login")
    String login(@Valid @ModelAttribute("loginForm") LoginForm form,
            BindingResult bindingResult, Model model, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return LOGIN_VIEW;
        }

        final LoginOutcome outcome;
        try {
            outcome = loginService.login(new LoginCommand(form.getIdentifier(), form.getPassword()));
        } catch (AuthenticationException exception) {
            model.addAttribute("errorMessage", GENERIC_FAILURE_MESSAGE);
            return LOGIN_VIEW;
        }

        cookies.addAuthenticationCookie(response, outcome.token());
        if (outcome.roles().contains(RoleCode.ADMIN)) {
            return "redirect:/admin/dashboard";
        }
        if (outcome.roles().contains(RoleCode.MANAGER)) {
            return "redirect:/manager/dashboard";
        }
        return "redirect:/";
    }
}
