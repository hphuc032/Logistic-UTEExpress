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
            var authorities = principal.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .collect(java.util.stream.Collectors.toSet());
            return new NavbarUser(principal.displayUsername(),
                    authorities.contains(RoleCode.VENDOR.authority()),
                    authorities.contains(RoleCode.MANAGER.authority()),
                    authorities.contains(RoleCode.ADMIN.authority()),
                    authorities.contains(RoleCode.SHIPPER.authority()));
        }
        return null;
    }
}
