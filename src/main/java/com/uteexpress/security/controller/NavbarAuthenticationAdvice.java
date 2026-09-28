package com.uteexpress.security.controller;

import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.dto.NavbarUser;
import com.uteexpress.security.RoleCode;
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
            boolean vendor = principal.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(RoleCode.VENDOR.authority()));
            return new NavbarUser(principal.displayUsername(), vendor);
        }
        return null;
    }
}
