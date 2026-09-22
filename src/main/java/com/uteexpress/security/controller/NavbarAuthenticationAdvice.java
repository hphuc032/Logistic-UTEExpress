package com.uteexpress.security.controller;

import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.dto.NavbarUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavbarAuthenticationAdvice {
    @ModelAttribute("navbarUser")
    NavbarUser navbarUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UteExpressPrincipal principal) {
            return new NavbarUser(principal.displayUsername());
        }
        return null;
    }
}
