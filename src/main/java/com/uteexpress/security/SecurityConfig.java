package com.uteexpress.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_ROUTES = {
            "/",
            "/products/**",
            "/categories/**",
            "/shops/**",
            "/login",
            "/register",
            "/verify-otp",
            "/forgot-password",
            "/reset-password",
            "/css/**",
            "/js/**",
            "/images/**",
            "/favicon.ico",
            "/api/v1/foundation",
            "/actuator/health"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_ROUTES).permitAll()
                        .requestMatchers("/user/**").hasAnyAuthority(
                                RoleCode.USER.authority(), RoleCode.VENDOR.authority())
                        .requestMatchers("/vendor/**").hasAuthority(RoleCode.VENDOR.authority())
                        .requestMatchers("/manager/**").hasAuthority(RoleCode.MANAGER.authority())
                        .requestMatchers("/admin/**").hasAuthority(RoleCode.ADMIN.authority())
                        .requestMatchers("/shipper/**").hasAuthority(RoleCode.SHIPPER.authority())
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
