package com.uteexpress.security.authentication;

import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.security.RoleCode;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class UteExpressPrincipalFactory {
    public UteExpressPrincipal create(AuthAccountSnapshot account, boolean includePasswordHash) {
        List<SimpleGrantedAuthority> authorities = account.roleCodes().stream()
                .map(UteExpressPrincipalFactory::roleCode)
                .sorted(Comparator.comparing(Enum::name))
                .map(role -> new SimpleGrantedAuthority(role.authority()))
                .toList();
        return new UteExpressPrincipal(
                account.userId(),
                account.displayUsername(),
                includePasswordHash ? account.passwordHash() : null,
                account.tokenVersion(),
                authorities,
                account.active());
    }

    private static RoleCode roleCode(String code) {
        try {
            return RoleCode.valueOf(code);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Account contains an unsupported role", exception);
        }
    }
}
