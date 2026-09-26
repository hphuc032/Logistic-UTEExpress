package com.uteexpress.security;

import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.identity.repository.UserRoleRepository;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.identity.service.RegistrationService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.jwt.JwtTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthenticationWebTest {
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerificationService;
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryService;
    @MockitoBean IdentityAuthenticationService identities;
    @MockitoBean RegistrationService registrationService;
    @MockitoBean UserRoleRepository userRoleRepository;
    @MockitoBean AuditLogService auditLogService;

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenService tokens;

    @Test
    void loginPageRendersCsrfProtectedFormWithoutPrefilledPassword() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string(containsString("Email hoặc tên đăng nhập")))
                .andExpect(content().string(containsString("type=\"password\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("value=\"RawSecret1\""))));
    }

    @Test
    void verifiedLoginPageShowsSafeSuccessMessage() throws Exception {
        mvc.perform(get("/login?verified=true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Xác minh email thành công. Bạn có thể đăng nhập.")));
    }

    @Test
    void loginWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/login")
                        .param("identifier", "user")
                        .param("password", "RawSecret1"))
                .andExpect(status().isForbidden());
        verify(identities, never()).findByLoginIdentifier(anyString());
    }

    @Test
    void activeAccountLoginSetsOnlySecurelyConfiguredHttpOnlyCookie() throws Exception {
        given(identities.findByLoginIdentifier("Phuc03"))
                .willReturn(Optional.of(account(true, 0L, "USER", passwordEncoder.encode("RawSecret1"))));

        var result = mvc.perform(post("/login").with(csrf())
                        .param("identifier", "Phuc03")
                        .param("password", "RawSecret1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andExpect(cookie().httpOnly("UTEEXPRESS_AUTH", true))
                .andExpect(cookie().secure("UTEEXPRESS_AUTH", false))
                .andExpect(cookie().path("UTEEXPRESS_AUTH", "/"))
                .andExpect(cookie().maxAge("UTEEXPRESS_AUTH", 1800))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(content().string(not(containsString("eyJ"))))
                .andReturn();

        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
        if (result.getRequest().getSession(false) != null) {
            assertThat(result.getRequest().getSession(false)
                    .getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        }
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("UTEEXPRESS_AUTH");
    }

    @Test
    void wrongUnknownAndInactiveAccountsUseSameGenericPublicFailure() throws Exception {
        String hash = passwordEncoder.encode("RawSecret1");
        given(identities.findByLoginIdentifier("wrong"))
                .willReturn(Optional.of(account(true, 0L, "USER", hash)));
        given(identities.findByLoginIdentifier("missing")).willReturn(Optional.empty());
        given(identities.findByLoginIdentifier("pending"))
                .willReturn(Optional.of(account(false, 0L, "USER", hash)));
        given(identities.findByLoginIdentifier("locked"))
                .willReturn(Optional.of(account(false, 0L, "USER", hash)));
        given(identities.findByLoginIdentifier("disabled"))
                .willReturn(Optional.of(account(false, 0L, "USER", hash)));

        for (String identifier : List.of("wrong", "missing", "pending", "locked", "disabled")) {
            String password = identifier.equals("wrong") ? "WrongSecret1" : "RawSecret1";
            mvc.perform(post("/login").with(csrf())
                            .param("identifier", identifier)
                            .param("password", password))
                    .andExpect(status().isOk())
                    .andExpect(view().name("auth/login"))
                    .andExpect(content().string(containsString(
                            "Không thể đăng nhập với thông tin đã cung cấp.")))
                    .andExpect(content().string(not(containsString(password))))
                    .andExpect(cookie().doesNotExist("UTEEXPRESS_AUTH"));
        }
    }

    @Test
    void jwtCookieAloneAuthenticatesStatelessRequestUsingCurrentDatabaseRole() throws Exception {
        String jwt = tokens.issue(principal("USER"));
        given(identities.findByUserId(42L))
                .willReturn(Optional.of(account(true, 0L, "ADMIN", "hash")));

        var result = mvc.perform(get("/admin/dashboard")
                        .cookie(new Cookie("UTEEXPRESS_AUTH", jwt)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
        if (result.getRequest().getSession(false) != null) {
            assertThat(result.getRequest().getSession(false)
                    .getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        }
        mvc.perform(get("/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void currentDatabaseRoleCanDenyTokenIssuedWhileUserHadAnotherRole() throws Exception {
        String jwt = tokens.issue(principal("ADMIN"));
        given(identities.findByUserId(42L))
                .willReturn(Optional.of(account(true, 0L, "USER", "hash")));

        mvc.perform(get("/admin/dashboard").cookie(new Cookie("UTEEXPRESS_AUTH", jwt)))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRequiresCsrfInvalidatesVersionAndExpiresCookie() throws Exception {
        String jwt = tokens.issue(principal("USER"));
        given(identities.findByUserId(42L))
                .willReturn(Optional.of(account(true, 0L, "USER", "hash")));

        mvc.perform(post("/logout").cookie(new Cookie("UTEEXPRESS_AUTH", jwt)))
                .andExpect(status().isForbidden());
        verify(identities, never()).invalidateTokens(42L);

        var result = mvc.perform(post("/logout").with(csrf()).cookie(new Cookie("UTEEXPRESS_AUTH", jwt)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout=true"))
                .andExpect(cookie().value("UTEEXPRESS_AUTH", ""))
                .andExpect(cookie().maxAge("UTEEXPRESS_AUTH", 0))
                .andExpect(cookie().httpOnly("UTEEXPRESS_AUTH", true))
                .andReturn();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> value.startsWith("UTEEXPRESS_AUTH=")
                        && value.contains("SameSite=Lax"));
        verify(identities).invalidateTokens(42L);
    }

    private AuthAccountSnapshot account(boolean active, long version, String role, String hash) {
        return new AuthAccountSnapshot(42L, "Phuc03", hash, active, version, Set.of(role));
    }

    private UteExpressPrincipal principal(String role) {
        return new UteExpressPrincipal(42L, "Phuc03", null, 0L,
                List.of(() -> RoleCode.valueOf(role).authority()), true);
    }
}
