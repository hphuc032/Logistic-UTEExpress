package com.uteexpress.security;

import com.uteexpress.security.authentication.UteExpressUserDetailsService;
import com.uteexpress.security.filter.JwtAuthenticationFilter;
import com.uteexpress.security.filter.CsrfCookieFilter;
import com.uteexpress.security.web.JwtLogoutSuccessHandler;
import com.uteexpress.security.web.TokenVersionLogoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
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
            "/verify-otp/resend",
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
            JsonAccessDeniedHandler accessDeniedHandler,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CsrfCookieFilter csrfCookieFilter,
            TokenVersionLogoutHandler tokenVersionLogoutHandler,
            JwtLogoutSuccessHandler logoutSuccessHandler,
            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
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
                        .accessDeniedHandler(accessDeniedHandler))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler(tokenVersionLogoutHandler)
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .permitAll())
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, LogoutFilter.class);
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(JwtProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .sameSite("Lax")
                .secure(properties.cookieSecure()));
        return repository;
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(
            UteExpressUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(DaoAuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CsrfCookieFilter> csrfCookieFilterRegistration(
            CsrfCookieFilter filter) {
        FilterRegistrationBean<CsrfCookieFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
