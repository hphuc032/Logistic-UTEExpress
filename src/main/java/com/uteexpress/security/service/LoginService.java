package com.uteexpress.security.service;

import com.uteexpress.security.RoleCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.dto.LoginCommand;
import com.uteexpress.security.dto.LoginOutcome;
import com.uteexpress.security.jwt.JwtTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LoginService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokens;

    public LoginService(AuthenticationManager authenticationManager, JwtTokenService tokens) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    public LoginOutcome login(LoginCommand command) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        command.identifier(), command.password()));
        UteExpressPrincipal principal = (UteExpressPrincipal) authentication.getPrincipal();
        Set<RoleCode> roles = principal.getAuthorities().stream()
                .map(authority -> RoleCode.fromAuthority(authority.getAuthority()))
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
        return new LoginOutcome(tokens.issue(principal), roles);
    }
}
