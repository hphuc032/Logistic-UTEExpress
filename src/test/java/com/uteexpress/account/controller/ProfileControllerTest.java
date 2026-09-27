package com.uteexpress.account.controller;

import com.uteexpress.account.dto.AvatarUpload;
import com.uteexpress.account.dto.ProfileForm;
import com.uteexpress.account.dto.ProfileView;
import com.uteexpress.account.service.ProfileService;
import com.uteexpress.common.storage.StoredContent;
import com.uteexpress.identity.service.CurrentPasswordMismatchException;
import com.uteexpress.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfileService profiles;
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService verification;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordReset;
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.identity.service.RegistrationService registration;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identities;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoles;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.governance.service.CategoryService categories;

    @BeforeEach
    void profileFixture() {
        when(profiles.currentProfile()).thenReturn(profile());
    }

    @Test
    void anonymousIsUnauthorizedUserAndVendorAreAllowedAndOtherRolesAreForbidden() throws Exception {
        mvc.perform(get("/user/profile")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"USER", "VENDOR"}) {
            mvc.perform(get("/user/profile").with(user("actor").roles(role)))
                    .andExpect(status().isOk());
        }
        for (String role : new String[]{"ADMIN", "MANAGER", "SHIPPER"}) {
            mvc.perform(get("/user/profile").with(user("actor").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void pageRendersEscapedProfileAndCsrfProtectedFormsWithoutSecretFields() throws Exception {
        when(profiles.currentProfile()).thenReturn(new ProfileView(
                "<script>alert(1)</script>", "safe@example.com", "<img src=x onerror=alert(1)>", "+84 90", false));

        mvc.perform(get("/user/profile").with(user("actor").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("account/profile"))
                .andExpect(content().string(containsString("UTEExpress")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(not(containsString("name=\"userId\""))))
                .andExpect(content().string(not(containsString("name=\"tokenVersion\""))))
                .andExpect(content().string(not(containsString("type=\"password\" id=\"newPassword\" name=\"newPassword\" value="))));
    }

    @Test
    void profileUpdateRequiresCsrfAndMassAssignmentFieldsNeverReachService() throws Exception {
        mvc.perform(post("/user/profile").with(user("actor").roles("USER"))
                        .param("fullName", "Nguyễn Văn A"))
                .andExpect(status().isForbidden());
        verify(profiles, never()).updateProfile(any());

        mvc.perform(post("/user/profile").with(user("actor").roles("USER")).with(csrf())
                        .param("fullName", "Nguyễn Văn A")
                        .param("phone", "+84 900 000 000")
                        .param("email", "attacker@example.com")
                        .param("username", "hacked")
                        .param("role", "ADMIN")
                        .param("status", "ACTIVE")
                        .param("tokenVersion", "0")
                        .param("avatarKey", "../../x")
                        .param("userId", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/profile"));

        ArgumentCaptor<ProfileForm> form = ArgumentCaptor.forClass(ProfileForm.class);
        verify(profiles).updateProfile(form.capture());
        assertThat(form.getValue().getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(form.getValue().getPhone()).isEqualTo("+84 900 000 000");
    }

    @Test
    void passwordChangeRequiresCsrfValidatesConfirmationAndClearsJwtOnSuccess() throws Exception {
        mvc.perform(post("/user/password").with(user("actor").roles("VENDOR"))
                        .param("currentPassword", "OldSecret1")
                        .param("newPassword", "NewSecret2")
                        .param("confirmPassword", "NewSecret2"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/user/password").with(user("actor").roles("USER")).with(csrf())
                        .param("currentPassword", "OldSecret1")
                        .param("newPassword", "NewSecret2")
                        .param("confirmPassword", "Different3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu xác nhận không khớp")));
        verify(profiles, never()).changePassword(any(), any());

        mvc.perform(post("/user/password").with(user("actor").roles("VENDOR")).with(csrf())
                        .param("currentPassword", "OldSecret1")
                        .param("newPassword", "NewSecret2")
                        .param("confirmPassword", "NewSecret2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?passwordChanged=true"));
        verify(profiles).changePassword("OldSecret1", "NewSecret2");

        mvc.perform(get("/login").param("passwordChanged", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại.")));
    }

    @Test
    void wrongCurrentPasswordIsSafeAndPasswordsAreNeverEchoed() throws Exception {
        doThrow(new CurrentPasswordMismatchException()).when(profiles)
                .changePassword("WrongSecret1", "NewSecret2");

        mvc.perform(post("/user/password").with(user("actor").roles("USER")).with(csrf())
                        .param("currentPassword", "WrongSecret1")
                        .param("newPassword", "NewSecret2")
                        .param("confirmPassword", "NewSecret2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu hiện tại không chính xác")))
                .andExpect(content().string(not(containsString("value=\"WrongSecret1\""))))
                .andExpect(content().string(not(containsString("value=\"NewSecret2\""))));
    }

    @Test
    void avatarUploadRequiresCsrfAndReadUsesSafeInlineHeaders() throws Exception {
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar", "client.png", "image/png", TestImages.png());
        mvc.perform(multipart("/user/avatar").file(avatar).with(user("actor").roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(multipart("/user/avatar").file(avatar)
                        .param("avatarKey", "../../forged")
                        .param("userId", "999")
                        .with(user("actor").roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/profile"));
        ArgumentCaptor<AvatarUpload> upload = ArgumentCaptor.forClass(AvatarUpload.class);
        verify(profiles).updateAvatar(upload.capture());
        assertThat(upload.getValue().originalFilename()).isEqualTo("client.png");

        when(profiles.currentAvatar()).thenReturn(Optional.of(
                new StoredContent(TestImages.png(), "image/png")));
        mvc.perform(get("/user/avatar").with(user("actor").roles("VENDOR")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    private static ProfileView profile() {
        return new ProfileView("profile-user", "user@example.com", "Nguyễn Văn A", "+84 90", false);
    }
}
