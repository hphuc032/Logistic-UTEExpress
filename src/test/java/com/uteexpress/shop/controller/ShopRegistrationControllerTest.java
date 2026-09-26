package com.uteexpress.shop.controller;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.shop.dto.ShopRegistrationRequest;
import com.uteexpress.shop.dto.ShopView;
import com.uteexpress.shop.service.ShopRegistrationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopRegistrationControllerTest {
    @MockitoBean com.uteexpress.identity.service.VendorRoleGrantService vendorRoleGrantService;
    @MockitoBean com.uteexpress.shop.service.ShopApprovalService shopApprovalService;
    @Autowired MockMvc mvc;

    @MockitoBean ShopRegistrationService shops;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService verification;
    @MockitoBean com.uteexpress.governance.service.CategoryService categories;
    @MockitoBean com.uteexpress.identity.service.RegistrationService registration;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identities;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoles;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;

    @Test
    void anonymousCannotReachShopApplication() throws Exception {
        mvc.perform(get("/user/shop"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userAndVendorCanViewWhileOtherSingleRolesCannot() throws Exception {
        for (String role : new String[]{"USER", "VENDOR"}) {
            mvc.perform(get("/user/shop").with(user("actor").roles(role)))
                    .andExpect(status().isOk());
        }
        for (String role : new String[]{"ADMIN", "MANAGER", "SHIPPER"}) {
            mvc.perform(get("/user/shop").with(user("actor").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void registrationPageRendersAndExistingShopRedirectsToStatus() throws Exception {
        when(shops.currentShop()).thenReturn(Optional.empty());
        mvc.perform(get("/user/shop/register").with(user("actor").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng ký cửa hàng")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("name=\"ownerId\""))))
                .andExpect(content().string(not(containsString("name=\"status\""))));

        when(shops.currentShop()).thenReturn(Optional.of(view("PENDING", "Shop", "shop", null)));
        mvc.perform(get("/user/shop/register").with(user("actor").roles("VENDOR")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/shop"));
    }

    @Test
    void unsafeRequestRequiresCsrf() throws Exception {
        mvc.perform(post("/user/shop/register").with(user("actor").roles("USER"))
                        .param("name", "Shop").param("slug", "shop").param("pickupAddress", "Pickup"))
                .andExpect(status().isForbidden());
        verify(shops, never()).register(any());
    }

    @Test
    void invalidInputWithCsrfDoesNotReachService() throws Exception {
        mvc.perform(post("/user/shop/register").with(user("actor").roles("USER")).with(csrf())
                        .param("name", "   ").param("slug", "../admin").param("pickupAddress", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tên cửa hàng")))
                .andExpect(content().string(containsString("Slug chỉ gồm")));
        verify(shops, never()).register(any());
    }

    @Test
    void validPostIgnoresSecuritySensitiveParametersAndRedirects() throws Exception {
        mvc.perform(post("/user/shop/register").with(user("actor").roles("USER")).with(csrf())
                        .param("name", "UTE Tech")
                        .param("slug", "My-Shop")
                        .param("description", "Thiết bị")
                        .param("pickupAddress", "01 Võ Văn Ngân")
                        .param("ownerId", "999")
                        .param("status", "APPROVED")
                        .param("rejectionReason", "none")
                        .param("role", "VENDOR")
                        .param("logoKey", "unsafe")
                        .param("bannerKey", "unsafe"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/shop"));

        ArgumentCaptor<ShopRegistrationRequest> request = ArgumentCaptor.forClass(ShopRegistrationRequest.class);
        verify(shops).register(request.capture());
        assertThat(request.getValue()).isEqualTo(
                new ShopRegistrationRequest("UTE Tech", "my-shop", "Thiết bị", "01 Võ Văn Ngân"));
    }

    @Test
    void conflictIsRenderedWithoutInternalDetails() throws Exception {
        when(shops.register(any())).thenThrow(new ApplicationException(ErrorCode.CONFLICT));

        mvc.perform(post("/user/shop/register").with(user("actor").roles("USER")).with(csrf())
                        .param("name", "Shop").param("slug", "shop").param("pickupAddress", "Pickup"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không thể gửi đơn với thông tin đã cung cấp")))
                .andExpect(content().string(not(containsString("constraint"))))
                .andExpect(content().string(not(containsString("DataIntegrityViolationException"))));
    }

    @Test
    void statusPageEscapesShopControlledText() throws Exception {
        when(shops.currentShop()).thenReturn(Optional.of(view(
                "REJECTED", "<script>alert(1)</script>", "safe-shop", "<img src=x onerror=alert(1)>")));

        mvc.perform(get("/user/shop").with(user("actor").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    private static ShopView view(String status, String name, String slug, String rejectionReason) {
        return new ShopView(1L, name, slug, "Mô tả", "Pickup", status, rejectionReason, 0L);
    }
}
