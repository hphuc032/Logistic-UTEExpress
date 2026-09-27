package com.uteexpress.identity.controller;

import com.uteexpress.identity.repository.UserRoleRepository;
import com.uteexpress.identity.service.EmailDispatchResult;
import com.uteexpress.identity.service.EmailVerificationResult;
import com.uteexpress.identity.service.EmailVerificationService;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.identity.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EmailVerificationControllerTest {
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean EmailVerificationService verification;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordResetService;
    @MockitoBean RegistrationService registration;
    @MockitoBean IdentityAuthenticationService identities;
    @MockitoBean UserRoleRepository userRoles;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.governance.service.CategoryService categories;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;

    @Autowired MockMvc mvc;

    @Test
    void anonymousVerificationPageRendersEscapedCsrfProtectedForms() throws Exception {
        mvc.perform(get("/verify-otp").param("identifier", "Phuc03"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/verify-otp"))
                .andExpect(content().string(containsString("Xác minh email")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("value=\"Phuc03\"")))
                .andExpect(content().string(not(containsString("th:utext"))));
    }

    @Test
    void verificationAndResendWithoutCsrfAreForbidden() throws Exception {
        mvc.perform(post("/verify-otp")
                        .param("identifier", "Phuc03")
                        .param("code", "123456"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/verify-otp/resend")
                        .param("identifier", "Phuc03"))
                .andExpect(status().isForbidden());

        verify(verification, never()).verify("Phuc03", "123456");
        verify(verification, never()).sendVerificationCode("Phuc03");
    }

    @Test
    void validCodeWithCsrfRedirectsToVerifiedLoginWithoutMassAssignment() throws Exception {
        given(verification.verify("Phuc03", "123456"))
                .willReturn(EmailVerificationResult.VERIFIED);

        mvc.perform(post("/verify-otp").with(csrf())
                        .param("identifier", "Phuc03")
                        .param("code", "123456")
                        .param("status", "ACTIVE")
                        .param("userId", "1")
                        .param("attempts", "0")
                        .param("purpose", "RESET_PASSWORD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?verified=true"));

        verify(verification).verify("Phuc03", "123456");
    }

    @Test
    void malformedCodeShowsValidationAndDoesNotReachServiceOrEchoCode() throws Exception {
        mvc.perform(post("/verify-otp").with(csrf())
                        .param("identifier", "Phuc03")
                        .param("code", "12x"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/verify-otp"))
                .andExpect(content().string(containsString("đúng 6 chữ số")))
                .andExpect(content().string(not(containsString("value=\"12x\""))));

        verify(verification, never()).verify("Phuc03", "12x");
    }

    @Test
    void invalidOtpUsesOneGenericPublicFailure() throws Exception {
        given(verification.verify("unknown", "123456"))
                .willReturn(EmailVerificationResult.INVALID);

        mvc.perform(post("/verify-otp").with(csrf())
                        .param("identifier", "unknown")
                        .param("code", "123456"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Mã xác minh không hợp lệ hoặc đã hết hạn.")))
                .andExpect(content().string(not(containsString("User not found"))))
                .andExpect(content().string(not(containsString("123456"))));
    }

    @Test
    void resendResponseIsIdenticalForAllInternalOutcomes() throws Exception {
        given(verification.sendVerificationCode("unknown"))
                .willReturn(EmailDispatchResult.NO_ACTION);
        given(verification.sendVerificationCode("active"))
                .willReturn(EmailDispatchResult.NO_ACTION);
        given(verification.sendVerificationCode("pending"))
                .willReturn(EmailDispatchResult.SENT);
        given(verification.sendVerificationCode("mail-failed"))
                .willReturn(EmailDispatchResult.DELIVERY_FAILED);

        String generic = "Nếu tài khoản hợp lệ và cần xác minh, mã xác minh sẽ được gửi";
        for (String identifier : new String[]{"unknown", "active", "pending", "mail-failed"}) {
            mvc.perform(post("/verify-otp/resend").with(csrf())
                            .param("identifier", identifier)
                            .param("purpose", "RESET_PASSWORD")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("auth/verify-otp"))
                    .andExpect(content().string(containsString(generic)))
                    .andExpect(content().string(not(containsString("không tồn tại"))))
                    .andExpect(content().string(not(containsString("đã kích hoạt"))));
        }
    }
}
