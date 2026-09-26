package com.uteexpress.security;

import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.identity.repository.UserRoleRepository;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.identity.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieWebTest {
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerificationService;
    private static final Pattern CSRF_VALUE = Pattern.compile(
            "name=\"_csrf\" value=\"([^\"]+)\"");

    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryService;
    @MockitoBean IdentityAuthenticationService identities;
    @MockitoBean RegistrationService registrationService;
    @MockitoBean UserRoleRepository userRoleRepository;
    @MockitoBean AuditLogService auditLogService;

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void thymeleafTokenAndCsrfCookieAuthorizeLoginWithoutHttpSessionSecurityState() throws Exception {
        given(identities.findByLoginIdentifier("Phuc03"))
                .willReturn(Optional.of(new AuthAccountSnapshot(
                        42L,
                        "Phuc03",
                        passwordEncoder.encode("RawSecret1"),
                        true,
                        0L,
                        Set.of("USER"))));

        var page = mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        var matcher = CSRF_VALUE.matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();

        var result = mvc.perform(post("/login")
                        .cookie(page.getResponse().getCookie("XSRF-TOKEN"))
                        .param("_csrf", matcher.group(1))
                        .param("identifier", "Phuc03")
                        .param("password", "RawSecret1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn();

        assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    }
}
