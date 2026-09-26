package com.uteexpress.security;

import com.uteexpress.identity.service.RegistrationService;
import com.uteexpress.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.uteexpress.security.authentication.UteExpressUserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(SecurityFoundationTest.SecurityTestConfiguration.class)
class SecurityFoundationTest {
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerificationService;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordResetService;
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryService;
    @MockitoBean
    private com.uteexpress.governance.service.AuditLogService auditLogService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private UserRoleRepository userRoleRepository;

    @MockitoBean
    private com.uteexpress.identity.service.IdentityAuthenticationService identityAuthenticationService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void foundationEndpointIsPublic() throws Exception {
        mvc.perform(get("/api/v1/foundation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FOUNDATION_READY"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anonymousRequestToProtectedEndpointGetsSafe401Contract() throws Exception {
        mvc.perform(get("/test-fixtures/security/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication is required."))
                .andExpect(jsonPath("$.path").value("/test-fixtures/security/protected"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("AuthenticationCredentialsNotFoundException"))));
    }

    @Test
    void authenticatedPrincipalCanAccessProtectedEndpoint() throws Exception {
        mvc.perform(get("/test-fixtures/security/protected").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string("protected"));
    }

    @Test
    void currentUserProviderReadsSubjectAndRolesFromSecurityContext() throws Exception {
        mvc.perform(get("/test-fixtures/security/current-user")
                        .with(user("alice@example.com").roles("USER", "VENDOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("alice@example.com"))
                .andExpect(jsonPath("$.roles", hasItems("USER", "VENDOR")))
                .andExpect(jsonPath("$.authorities", hasItems("ROLE_USER", "ROLE_VENDOR")));
    }

    @Test
    void insufficientRoleGetsSafe403ContractFromMethodSecurity() throws Exception {
        mvc.perform(get("/test-fixtures/security/admin-operation").with(user("alice").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access is denied."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("AccessDeniedException"))));
    }

    @Test
    void matchingRolePassesPreAuthorize() throws Exception {
        mvc.perform(get("/test-fixtures/security/admin-operation").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void roleRouteConventionRejectsInsufficientAuthorityBeforeMvc() throws Exception {
        mvc.perform(get("/admin/not-created").with(user("alice").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void matchingRolePassesRoutePolicyWithoutCreatingFakeEndpoint() throws Exception {
        mvc.perform(get("/admin/not-created").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unsafeRequestWithoutCsrfIsDenied() throws Exception {
        mvc.perform(post("/test-fixtures/security/protected").with(user("alice")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unsafeRequestWithCsrfCanReachController() throws Exception {
        mvc.perform(post("/test-fixtures/security/protected").with(user("alice")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("protected-post"));
    }

    @Test
    void passwordEncoderUsesOneWayBcryptHash() {
        String rawPassword = "a-long-demo-password";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(encodedPassword.startsWith("$2"));
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
    }

    @Test
    void applicationUsesOnlyItsDatabaseBackedUserDetailsService() {
        var services = applicationContext.getBeansOfType(UserDetailsService.class);
        assertTrue(services.size() == 1);
        assertTrue(services.values().iterator().next() instanceof UteExpressUserDetailsService);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SecurityTestConfiguration {
        @Bean
        SecurityFixtureService securityFixtureService() {
            return new SecurityFixtureService();
        }

        @Bean
        SecurityFixtureController securityFixtureController(SecurityFixtureService service,
                CurrentUserProvider currentUserProvider) {
            return new SecurityFixtureController(service, currentUserProvider);
        }
    }

    static class SecurityFixtureService {
        @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
        String adminOperation() {
            return "admin";
        }
    }

    @RestController
    @RequestMapping("/test-fixtures/security")
    static class SecurityFixtureController {
        private final SecurityFixtureService service;
        private final CurrentUserProvider currentUserProvider;

        SecurityFixtureController(SecurityFixtureService service, CurrentUserProvider currentUserProvider) {
            this.service = service;
            this.currentUserProvider = currentUserProvider;
        }

        @GetMapping("/protected")
        String protectedEndpoint() {
            return "protected";
        }

        @PostMapping("/protected")
        String protectedPost() {
            return "protected-post";
        }

        @GetMapping("/current-user")
        CurrentUser currentUser() {
            return currentUserProvider.requireCurrentUser();
        }

        @GetMapping("/admin-operation")
        String adminOperation() {
            return service.adminOperation();
        }
    }
}
