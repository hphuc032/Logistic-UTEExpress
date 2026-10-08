package com.uteexpress.account.controller;

import com.uteexpress.account.dto.AddressForm;
import com.uteexpress.account.dto.AddressView;
import com.uteexpress.account.service.AddressService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AddressControllerTest {
    @MockitoBean com.uteexpress.governance.service.ProductModerationService productModerationService;
    @MockitoBean com.uteexpress.governance.service.ShopModerationService shopModerationService;
    @MockitoBean com.uteexpress.catalog.service.ProductService productService;
    @MockitoBean com.uteexpress.catalog.service.InventoryService inventoryService;
    @MockitoBean com.uteexpress.governance.service.CategoryQueryService categoryQueryService;
    @MockitoBean com.uteexpress.shop.service.VendorShopQueryService vendorShopQueryService;
    @MockitoBean com.uteexpress.identity.service.VendorRoleGrantService vendorRoleGrantService;
    @MockitoBean com.uteexpress.shop.service.ShopApprovalService shopApprovalService;
    @Autowired MockMvc mvc;
    @MockitoBean AddressService addresses;
    @MockitoBean com.uteexpress.governance.service.AccountGovernanceService accountGovernanceService;
    @MockitoBean com.uteexpress.identity.service.IdentityAccountGovernanceService identityAccountGovernanceService;
    @MockitoBean com.uteexpress.account.service.ProfileService profiles;
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
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService verification;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordReset;
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.identity.service.RegistrationService registration;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identities;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoles;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.governance.service.CategoryService categories;

    @BeforeEach
    void fixture() {
        when(addresses.listCurrentAddresses()).thenReturn(List.of());
    }

    @Test
    void anonymousIsUnauthorizedUserAndVendorAreAllowedAndOtherRolesForbidden() throws Exception {
        mvc.perform(get("/user/addresses")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"USER", "VENDOR"}) {
            mvc.perform(get("/user/addresses").with(user("actor").roles(role)))
                    .andExpect(status().isOk());
        }
        for (String role : new String[]{"ADMIN", "MANAGER", "SHIPPER"}) {
            mvc.perform(get("/user/addresses").with(user("actor").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void pageEscapesAddressContentAndRendersDefaultAndEmptyStates() throws Exception {
        mvc.perform(get("/user/addresses").with(user("actor").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("account/addresses"))
                .andExpect(content().string(containsString("Chưa có địa chỉ")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        when(addresses.listCurrentAddresses()).thenReturn(List.of(new AddressView(5L,
                "<script>alert(1)</script>", "+84 90", "79", "Thủ Đức",
                "<img src=x onerror=alert(1)>", true)));
        mvc.perform(get("/user/addresses").with(user("actor").roles("VENDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mặc định")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(not(containsString("name=\"userId\""))))
                .andExpect(content().string(not(containsString("name=\"isDefault\""))));
    }

    @Test
    void createRequiresCsrfValidatesAndIgnoresMassAssignment() throws Exception {
        mvc.perform(post("/user/addresses").with(user("actor").roles("USER"))
                        .param("receiverName", "Receiver"))
                .andExpect(status().isForbidden());
        verify(addresses, never()).create(any());

        mvc.perform(post("/user/addresses").with(user("actor").roles("USER")).with(csrf())
                        .param("receiverName", " ").param("phone", "x")
                        .param("provinceCode", " ").param("district", " ").param("detail", " "))
                .andExpect(status().isOk())
                .andExpect(view().name("account/addresses"))
                .andExpect(content().string(containsString("Tên người nhận là bắt buộc")));
        verify(addresses, never()).create(any());

        when(addresses.create(any())).thenReturn(address());
        mvc.perform(post("/user/addresses").with(user("actor").roles("VENDOR")).with(csrf())
                        .param("receiverName", "  Nguyễn Văn A  ")
                        .param("phone", " +84 900-000-000 ")
                        .param("provinceCode", " 79 ").param("district", " Thủ Đức ")
                        .param("detail", " 01 Võ Văn Ngân ")
                        .param("userId", "999").param("id", "123")
                        .param("isDefault", "true").param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/addresses"));
        ArgumentCaptor<AddressForm> form = ArgumentCaptor.forClass(AddressForm.class);
        verify(addresses).create(form.capture());
        assertThat(form.getValue().getReceiverName()).isEqualTo("Nguyễn Văn A");
        assertThat(form.getValue().getProvinceCode()).isEqualTo("79");
    }

    @Test
    void updateDeleteAndDefaultRequireCsrfAndUseOnlyPathAddressId() throws Exception {
        mvc.perform(post("/user/addresses/12/update").with(user("actor").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/addresses/12/delete").with(user("actor").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/addresses/12/default").with(user("actor").roles("USER")))
                .andExpect(status().isForbidden());

        when(addresses.update(any(), any())).thenReturn(address());
        mvc.perform(post("/user/addresses/12/update").with(user("actor").roles("USER")).with(csrf())
                        .param("receiverName", "Receiver").param("phone", "+84 90")
                        .param("provinceCode", "79").param("district", "Thủ Đức")
                        .param("detail", "01 Võ Văn Ngân").param("userId", "999")
                        .param("isDefault", "true"))
                .andExpect(status().is3xxRedirection());
        verify(addresses).update(org.mockito.ArgumentMatchers.eq(12L), any(AddressForm.class));

        mvc.perform(post("/user/addresses/12/delete").with(user("actor").roles("VENDOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(addresses).delete(12L);
        mvc.perform(post("/user/addresses/12/default").with(user("actor").roles("VENDOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(addresses).setDefault(12L);
    }

    @Test
    void invalidUpdateRendersFieldErrorsForTheOwnedAddressForm() throws Exception {
        when(addresses.listCurrentAddresses()).thenReturn(List.of(address()));

        mvc.perform(post("/user/addresses/12/update")
                        .with(user("actor").roles("USER")).with(csrf())
                        .param("receiverName", " ").param("phone", "bad-phone")
                        .param("provinceCode", " ").param("district", " ").param("detail", " "))
                .andExpect(status().isOk())
                .andExpect(view().name("account/addresses"))
                .andExpect(content().string(containsString("Tên người nhận là bắt buộc")))
                .andExpect(content().string(containsString("Số điện thoại chứa ký tự không hợp lệ")));
        verify(addresses, never()).update(any(), any());
    }

    @Test
    void crossUserIdentifierReturnsSafeNotFoundWithoutOwnershipLeak() throws Exception {
        doThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND)).when(addresses).delete(101L);
        mvc.perform(post("/user/addresses/101/delete")
                        .with(user("actor").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("belongs"))))
                .andExpect(content().string(not(containsString("user_id"))));
    }

    private static AddressView address() {
        return new AddressView(12L, "Receiver", "+84 90", "79", "Thủ Đức",
                "01 Võ Văn Ngân", true);
    }
}
