package com.uteexpress.governance;

import com.uteexpress.governance.dto.AccountView;
import com.uteexpress.governance.service.AccountGovernanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class ModerationWebTest {
    @MockitoBean com.uteexpress.governance.service.ProductModerationService productModerationService;
    @MockitoBean com.uteexpress.governance.service.ShopModerationService shopModerationService;
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.identity.service.VendorRoleGrantService vendorRoleGrantService;
    @MockitoBean com.uteexpress.shop.service.ShopApprovalService shopApprovalService;
    @MockitoBean com.uteexpress.account.service.AddressService addressService;
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.promotion.service.VoucherService voucherService;
    @MockitoBean com.uteexpress.promotion.service.VendorVoucherService vendorVoucherService;
    @MockitoBean com.uteexpress.order.service.OrderPlacementService orderPlacementService;
    @MockitoBean com.uteexpress.order.service.BuyerOrderService buyerOrderService;
    @MockitoBean com.uteexpress.order.service.VendorOrderService vendorOrderService;
    @MockitoBean com.uteexpress.order.service.VendorOrderAuthority vendorOrderAuthority;
    @MockitoBean com.uteexpress.payment.service.PaymentReadService paymentReadService;
    @MockitoBean com.uteexpress.payment.service.PaymentService paymentService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerificationService;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordResetService;
    @MockitoBean com.uteexpress.governance.service.CategoryService categories;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.identity.service.RegistrationService registration;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identities;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoles;
    @MockitoBean com.uteexpress.catalog.service.ProductService productService;
    @MockitoBean com.uteexpress.catalog.service.InventoryService inventoryService;
    @MockitoBean com.uteexpress.governance.service.CategoryQueryService categoryQueryService;
    @MockitoBean com.uteexpress.shop.service.VendorShopQueryService vendorShopQueryService;
    @MockitoBean AccountGovernanceService accounts;
    @MockitoBean com.uteexpress.identity.service.IdentityAccountGovernanceService identityAccountGovernanceService;
    @Autowired MockMvc mvc;

    @Test void opsCanSearchAndSubmitOnlyVersionAndReason() throws Exception {
        var item = new com.uteexpress.governance.dto.ModerationView(7L, "Test product", "ACTIVE", 2L, null);
        when(productModerationService.search("", 0)).thenReturn(new PageImpl<>(List.of(item)));
        var shop = new com.uteexpress.governance.dto.ModerationView(7L, "Test shop", "APPROVED", 2L, null);
        when(shopModerationService.search("", 0)).thenReturn(new PageImpl<>(List.of(shop)));
        for (String role : List.of("ADMIN", "MANAGER")) {
            String ops = role.toLowerCase();
            mvc.perform(get("/" + ops + "/products").with(user("ops").roles(role)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Test product")))
                .andExpect(content().string(containsString("action=\"/" + ops + "/products/7/hide\"")));
            mvc.perform(get("/" + ops + "/shops/moderation").with(user("ops").roles(role)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Test shop")))
                .andExpect(content().string(containsString("action=\"/" + ops + "/shops/7/suspend\"")));
            mvc.perform(post("/" + ops + "/products/7/hide").with(user("ops").roles(role)).with(csrf())
                .param("version", "2").param("reason", "Violation").param("actorId", "999").param("status", "ACTIVE"))
                .andExpect(redirectedUrl("/" + ops + "/products"));
        }
        verify(productModerationService, times(2)).change(7L, 2L, true, "Violation");
        mvc.perform(post("/manager/shops/7/suspend").with(user("ops").roles("MANAGER")).with(csrf())
            .param("version", "2").param("reason", "Violation")).andExpect(redirectedUrl("/manager/shops/moderation"));
        verify(shopModerationService).change(7L, 2L, true, "Violation");
    }

    @Test void csrfAndRoleBoundariesProtectBothTargets() throws Exception {
        for (String target : List.of("products/7/hide", "shops/7/suspend")) {
            mvc.perform(post("/admin/" + target).with(user("ops").roles("ADMIN"))
                .param("version", "2").param("reason", "Violation")).andExpect(status().isForbidden());
            for (String role : List.of("USER", "VENDOR", "SHIPPER")) {
                mvc.perform(post("/manager/" + target).with(user("other").roles(role)).with(csrf())
                    .param("version", "2").param("reason", "Violation")).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(productModerationService, shopModerationService);
    }
}
