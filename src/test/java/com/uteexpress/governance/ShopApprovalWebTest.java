package com.uteexpress.governance;

import com.uteexpress.shop.dto.ShopApprovalView;
import com.uteexpress.shop.service.ShopApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ShopApprovalWebTest {
    @MockitoBean com.uteexpress.identity.service.VendorRoleGrantService vendorRoleGrantService;
    @MockitoBean ShopApprovalService approvals;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService registration;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerification;
    @MockitoBean com.uteexpress.governance.service.CategoryService categories;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.identity.service.RegistrationService identityRegistration;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identities;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoles;
    @Autowired MockMvc mvc;

    @Test void onlyAdminMayReviewAndPostsRequireCsrf() throws Exception {
        mvc.perform(get("/admin/shops")).andExpect(status().isUnauthorized());
        for (String role : List.of("USER", "VENDOR", "MANAGER", "SHIPPER")) {
            mvc.perform(get("/admin/shops").with(user("actor").roles(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/shops/1/approve").with(user("actor").roles(role)).with(csrf())
                    .param("version", "0")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/shops/1/approve").with(user("actor").roles("ADMIN"))
                .param("version", "0")).andExpect(status().isForbidden());
        verifyNoInteractions(approvals);
    }

    @Test void listAndDetailEscapeUntrustedShopText() throws Exception {
        ShopApprovalView shop = new ShopApprovalView(1L, 2L, "<script>alert(1)</script>",
                "xss-shop", "<b>unsafe</b>", "Pickup", "PENDING", null, Instant.now(), 0L);
        when(approvals.pending(0)).thenReturn(new PageImpl<>(List.of(shop)));
        when(approvals.get(1L)).thenReturn(shop);
        mvc.perform(get("/admin/shops").with(user("actor").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")));
        mvc.perform(get("/admin/shops/1").with(user("actor").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;b&gt;unsafe&lt;/b&gt;")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    @Test void approvalAndRejectionUseServerActorAndPostedVersion() throws Exception {
        mvc.perform(post("/admin/shops/1/approve").with(user("actor").roles("ADMIN")).with(csrf())
                .param("version", "4").param("actorId", "999").param("ownerId", "999"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/shops/1"));
        verify(approvals).approve(1L, 4L);
        mvc.perform(post("/admin/shops/2/reject").with(user("actor").roles("ADMIN")).with(csrf())
                .param("version", "5").param("reason", "Thông tin chưa đầy đủ"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/shops/2"));
        verify(approvals).reject(2L, 5L, "Thông tin chưa đầy đủ");
    }
}
