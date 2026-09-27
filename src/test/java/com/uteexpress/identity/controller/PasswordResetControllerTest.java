package com.uteexpress.identity.controller;

import com.uteexpress.identity.repository.UserRoleRepository;
import com.uteexpress.identity.service.EmailDispatchResult;
import com.uteexpress.identity.service.EmailVerificationService;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.identity.service.PasswordResetResult;
import com.uteexpress.identity.service.PasswordResetService;
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
class PasswordResetControllerTest {
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean EmailVerificationService verification;
    @MockitoBean PasswordResetService passwordReset;
    @MockitoBean RegistrationService registration;
    @MockitoBean IdentityAuthenticationService identities;
    @MockitoBean UserRoleRepository userRoles;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.governance.service.CategoryService categories;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;

    @Autowired MockMvc mvc;

    @Test
    void anonymousPagesRenderSharedCsrfProtectedFormsWithoutUnsafeOutput() throws Exception {
        mvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(content().string(containsString("Quên mật khẩu")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mvc.perform(get("/reset-password").param("requested", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(content().string(containsString("Đặt lại mật khẩu")))
                .andExpect(content().string(containsString(
                        "nếu tài khoản hợp lệ, mã xác nhận sẽ được gửi")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("th:utext"))));
    }

    @Test
    void unsafeRequestsWithoutCsrfAreForbidden() throws Exception {
        mvc.perform(post("/forgot-password").param("email", "user@example.com"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/reset-password")
                        .param("email", "user@example.com")
                        .param("code", "123456")
                        .param("newPassword", "NewSecret1")
                        .param("confirmPassword", "NewSecret1"))
                .andExpect(status().isForbidden());

        verify(passwordReset, never()).sendResetCode("user@example.com");
        verify(passwordReset, never()).resetPassword(
                "user@example.com", "123456", "NewSecret1");
    }

    @Test
    void forgotResponseIsIdenticalForKnownUnknownAndMailFailure() throws Exception {
        given(passwordReset.sendResetCode("active@example.com"))
                .willReturn(EmailDispatchResult.SENT);
        given(passwordReset.sendResetCode("unknown@example.com"))
                .willReturn(EmailDispatchResult.NO_ACTION);
        given(passwordReset.sendResetCode("failed@example.com"))
                .willReturn(EmailDispatchResult.DELIVERY_FAILED);

        for (String email : new String[]{
                "active@example.com", "unknown@example.com", "failed@example.com"}) {
            mvc.perform(post("/forgot-password").with(csrf())
                            .param("email", email)
                            .param("status", "ACTIVE")
                            .param("purpose", "EMAIL_VERIFICATION"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/reset-password?requested=true"));
        }
    }

    @Test
    void invalidForgotFormDoesNotReachService() throws Exception {
        mvc.perform(post("/forgot-password").with(csrf()).param("email", "not-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(content().string(containsString("Email không đúng định dạng")));

        verify(passwordReset, never()).sendResetCode("not-email");
    }

    @Test
    void invalidOtpUsesOneGenericFailureAndNeverEchoesSecrets() throws Exception {
        given(passwordReset.resetPassword(
                "user@example.com", "123456", "NewSecret1"))
                .willReturn(PasswordResetResult.INVALID);

        mvc.perform(post("/reset-password").with(csrf())
                        .param("email", "user@example.com")
                        .param("code", "123456")
                        .param("newPassword", "NewSecret1")
                        .param("confirmPassword", "NewSecret1")
                        .param("tokenVersion", "0")
                        .param("passwordHash", "forged"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/reset-password"))
                .andExpect(content().string(containsString(
                        "Mã xác nhận không hợp lệ hoặc đã hết hạn.")))
                .andExpect(content().string(not(containsString("value=\"123456\""))))
                .andExpect(content().string(not(containsString("value=\"NewSecret1\""))))
                .andExpect(content().string(not(containsString("forged"))));
    }

    @Test
    void validationStopsMismatchedPasswordAndSuccessRedirectsToLogin() throws Exception {
        mvc.perform(post("/reset-password").with(csrf())
                        .param("email", "user@example.com")
                        .param("code", "123456")
                        .param("newPassword", "NewSecret1")
                        .param("confirmPassword", "Different1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu xác nhận không khớp")));
        verify(passwordReset, never()).resetPassword(
                "user@example.com", "123456", "NewSecret1");

        given(passwordReset.resetPassword(
                "user@example.com", "123456", "NewSecret1"))
                .willReturn(PasswordResetResult.RESET);
        mvc.perform(post("/reset-password").with(csrf())
                        .param("email", "user@example.com")
                        .param("code", "123456")
                        .param("newPassword", "NewSecret1")
                        .param("confirmPassword", "NewSecret1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordReset=true"));

        mvc.perform(get("/login").param("passwordReset", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Mật khẩu đã được đặt lại. Bạn có thể đăng nhập bằng mật khẩu mới.")));
    }
}
