package com.uteexpress.identity.controller;

import com.uteexpress.identity.dto.RegistrationCommand;
import com.uteexpress.identity.dto.RegistrationOutcome;
import com.uteexpress.identity.service.RegistrationConflictException;
import com.uteexpress.identity.service.RegistrationService;
import com.uteexpress.identity.service.EmailDispatchResult;
import com.uteexpress.identity.service.EmailVerificationService;
import com.uteexpress.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
class RegistrationControllerTest {
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean EmailVerificationService emailVerificationService;
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryService;
    @MockitoBean
    private com.uteexpress.governance.service.AuditLogService auditLogService;

    @Autowired MockMvc mvc;

    @MockitoBean RegistrationService registrationService;

    @MockitoBean UserRoleRepository userRoleRepository;

    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identityAuthenticationService;

    @Test
    void getRegisterRendersPublicFormWithCsrfAndEmptyPasswordInputs() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(content().string(containsString("id=\"register-title\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("type=\"password\"")))
                .andExpect(content().string(not(containsString("value=\"RawSecret1\""))));
    }

    @Test
    void postWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(post("/register")
                        .param("email", "user@example.com")
                        .param("username", "Phuc03")
                        .param("password", "RawSecret1")
                        .param("confirmPassword", "RawSecret1"))
                .andExpect(status().isForbidden());

        verify(registrationService, never()).register(any());
    }

    @Test
    void invalidPostDisplaysValidationWithoutCallingServiceOrEchoingPassword() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("email", "invalid")
                        .param("username", "bad user")
                        .param("password", "RawSecret1")
                        .param("confirmPassword", "differentPass1"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(content().string(containsString("Email không đúng định dạng.")))
                .andExpect(content().string(containsString("Mật khẩu xác nhận không khớp.")))
                .andExpect(content().string(not(containsString("RawSecret1"))))
                .andExpect(content().string(not(containsString("differentPass1"))));

        verify(registrationService, never()).register(any());
    }

    @Test
    void validPostRegistersAndRedirectsUsingPostRedirectGet() throws Exception {
        given(registrationService.register(any(RegistrationCommand.class)))
                .willReturn(new RegistrationOutcome("Phuc03"));
        given(emailVerificationService.sendVerificationCode("Phuc03"))
                .willReturn(EmailDispatchResult.SENT);

        mvc.perform(post("/register").with(csrf())
                        .param("email", "user@example.com")
                        .param("username", "Phuc03")
                        .param("password", "RawSecret1")
                        .param("confirmPassword", "RawSecret1")
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-otp?identifier=Phuc03"));

        verify(registrationService).register(new RegistrationCommand(
                "user@example.com", "Phuc03", "RawSecret1"));
        verify(emailVerificationService).sendVerificationCode("Phuc03");
    }

    @Test
    void duplicateIdentityUsesGenericMessageAndDoesNotExposePassword() throws Exception {
        given(registrationService.register(any(RegistrationCommand.class)))
                .willThrow(new RegistrationConflictException());

        mvc.perform(post("/register").with(csrf())
                        .param("email", "user@example.com")
                        .param("username", "Phuc03")
                        .param("password", "RawSecret1")
                        .param("confirmPassword", "RawSecret1"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(content().string(containsString(
                        "Không thể tạo tài khoản với thông tin đã cung cấp.")))
                .andExpect(content().string(not(containsString("RawSecret1"))))
                .andExpect(content().string(not(containsString("constraint"))));
    }

    @Test
    void registeredRedirectTargetExplainsPendingVerificationWithoutClaimingOtpWasSent() throws Exception {
        mvc.perform(get("/register?registered=true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Tài khoản đã được tạo. Bạn cần xác minh email trước khi đăng nhập.")))
                .andExpect(content().string(not(containsString("OTP đã gửi"))));
    }
}
