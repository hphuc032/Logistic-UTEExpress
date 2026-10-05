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
class AccountGovernanceWebTest {
    @MockitoBean com.uteexpress.governance.service.ProductModerationService productModerationService;
    @MockitoBean com.uteexpress.governance.service.ShopModerationService shopModerationService;
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.identity.service.VendorRoleGrantService vendorRoleGrantService;
    @MockitoBean com.uteexpress.shop.service.ShopApprovalService shopApprovalService;
    @MockitoBean com.uteexpress.account.service.AddressService addressService;
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
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
    @MockitoBean com.uteexpress.governance.service.RoleGovernanceService roleGovernance;
    @MockitoBean com.uteexpress.identity.service.IdentityAccountGovernanceService identityAccountGovernanceService;
    @Autowired MockMvc mvc;

    @Test void managerCanReadButCannotMutateAndCsrfIsRequired() throws Exception {
        var account = new AccountView(7L, "buyer", "buyer@example.test", "Buyer", "0123", "ACTIVE", 2L);
        when(accounts.search("", 0)).thenReturn(new PageImpl<>(List.of(account)));
        when(accounts.get(7L)).thenReturn(account);
        when(roleGovernance.rolesFor(7L)).thenReturn(java.util.Set.of("USER"));
        mvc.perform(get("/manager/accounts").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("buyer@example.test")));
        mvc.perform(get("/manager/accounts/7").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("0123")));
        mvc.perform(post("/manager/accounts/7/lock").with(user("manager").roles("MANAGER")).with(csrf())
                .param("version", "2")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/accounts/7/lock").with(user("admin").roles("ADMIN"))
                .param("version", "2")).andExpect(status().isForbidden());
        verify(accounts, never()).setLocked(any(), any(), anyBoolean());
    }

    @Test void adminCanSubmitVersionedLockAndUnauthorizedUsersCannotRead() throws Exception {
        mvc.perform(get("/admin/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/accounts").with(user("buyer").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/accounts/7/lock").with(user("admin").roles("ADMIN")).with(csrf())
                .param("version", "2").param("actorId", "999").param("status", "DISABLED"))
                .andExpect(status().is3xxRedirection());
        verify(accounts).setLocked(7L, 2L, true);
    }
}
