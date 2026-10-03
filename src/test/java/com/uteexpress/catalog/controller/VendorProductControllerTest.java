package com.uteexpress.catalog.controller;

import com.uteexpress.catalog.dto.ProductCreateRequest;
import com.uteexpress.catalog.dto.ProductImageView;
import com.uteexpress.catalog.dto.ProductUpdateRequest;
import com.uteexpress.catalog.dto.ProductView;
import com.uteexpress.catalog.service.InventoryService;
import com.uteexpress.catalog.service.ProductService;
import com.uteexpress.common.storage.StoredContent;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.governance.dto.CategoryOption;
import com.uteexpress.governance.service.CategoryQueryService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class VendorProductControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean com.uteexpress.governance.service.ProductModerationService productModerationService;
    @MockitoBean com.uteexpress.governance.service.ShopModerationService shopModerationService;
    @MockitoBean ProductService products;
    @MockitoBean InventoryService inventory;
    @MockitoBean CategoryQueryService categories;
    @MockitoBean com.uteexpress.shop.service.VendorShopQueryService vendorShops;
    @MockitoBean com.uteexpress.account.service.AddressService addressService;
    @MockitoBean com.uteexpress.account.service.ProfileService profiles;
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.order.service.OrderPlacementService orderPlacementService;
    @MockitoBean com.uteexpress.shop.service.ShopApprovalService shopApprovalService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.identity.service.VendorRoleGrantService vendorRoleGrantService;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService verification;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordReset;
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.identity.service.RegistrationService registration;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identities;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoles;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryCommands;
    @MockitoBean com.uteexpress.governance.service.AccountGovernanceService accountGovernanceService;
    @MockitoBean com.uteexpress.identity.service.IdentityAccountGovernanceService identityAccountGovernanceService;

    @BeforeEach
    void fixture() {
        when(products.listCurrentShopProducts()).thenReturn(List.of());
        when(categories.listActiveCategories()).thenReturn(List.of(new CategoryOption(5L, "Thiết bị", true)));
        when(products.getCurrentShopProduct(10L)).thenReturn(product());
    }

    @Test
    void vendorRequiredForEveryManagementPage() throws Exception {
        mvc.perform(get("/vendor/products")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"USER", "ADMIN", "MANAGER", "SHIPPER"}) {
            mvc.perform(get("/vendor/products").with(user("actor").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/vendor/products").with(user("vendor").roles("VENDOR")))
                .andExpect(status().isOk()).andExpect(view().name("vendor/products/list"));
    }

    @Test
    void listEscapesProductContentAndRendersAuthenticatedImageRoute() throws Exception {
        when(products.listCurrentShopProducts()).thenReturn(List.of(new ProductView(10L,
                "<script>alert(1)</script>", "<img src=x>", new BigDecimal("100000"), 2,
                "ACTIVE", 5L, "Thiết bị", true, 3L,
                List.of(new ProductImageView(8L, 0, "<svg onload=alert(1)>")))));

        mvc.perform(get("/vendor/products").with(user("vendor").roles("VENDOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("/vendor/products/10/images/8/content")));
    }

    @Test
    void createAndEditFormsRenderValidationAndCsrf() throws Exception {
        mvc.perform(get("/vendor/products/new").with(user("vendor").roles("VENDOR")))
                .andExpect(status().isOk()).andExpect(view().name("vendor/products/form"))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("Chọn danh mục")));

        mvc.perform(post("/vendor/products").with(user("vendor").roles("VENDOR")).with(csrf())
                        .param("name", " ").param("price", "100.50").param("stock", "-1")
                        .param("categoryId", "5"))
                .andExpect(status().isOk()).andExpect(view().name("vendor/products/form"))
                .andExpect(content().string(containsString("Tên sản phẩm là bắt buộc")));
        verify(products, never()).create(any());

        mvc.perform(post("/vendor/products").with(user("vendor").roles("VENDOR")).with(csrf())
                        .param("name", "Product").param("price", "0").param("stock", "1")
                        .param("categoryId", "5"))
                .andExpect(status().isOk()).andExpect(view().name("vendor/products/form"));
        verify(products, never()).create(any());
    }

    @Test
    void validCreateUsesDtoWhitelistAndRedirects() throws Exception {
        when(products.create(any())).thenReturn(product());
        mvc.perform(post("/vendor/products").with(user("vendor").roles("VENDOR")).with(csrf())
                        .param("name", " Product ").param("description", " Description ")
                        .param("price", "150000").param("stock", "4").param("categoryId", "5")
                        .param("shopId", "999").param("ownerId", "999").param("status", "HIDDEN")
                        .param("id", "999").param("version", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/products/10/edit"));

        ArgumentCaptor<ProductCreateRequest> request = ArgumentCaptor.forClass(ProductCreateRequest.class);
        verify(products).create(request.capture());
        assertThat(request.getValue().getName()).isEqualTo("Product");
        assertThat(request.getValue().getDescription()).isEqualTo("Description");
        assertThat(ProductCreateRequest.class.getDeclaredFields()).extracting("name")
                .containsExactlyInAnyOrder("name", "description", "price", "stock", "categoryId");
    }

    @Test
    void everyMutationRequiresCsrfAndValidCsrfInvokesService() throws Exception {
        var vendor = user("vendor").roles("VENDOR");
        mvc.perform(post("/vendor/products").with(vendor)).andExpect(status().isForbidden());
        mvc.perform(post("/vendor/products/10/update").with(vendor)).andExpect(status().isForbidden());
        mvc.perform(post("/vendor/products/10/delete").with(vendor).param("version", "3"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/vendor/products/10/images").file(new MockMultipartFile(
                        "image", "p.png", "image/png", new byte[]{1})).with(vendor))
                .andExpect(status().isForbidden());
        mvc.perform(post("/vendor/products/10/images/8/delete").with(vendor))
                .andExpect(status().isForbidden());

        mvc.perform(post("/vendor/products/10/delete").with(vendor).with(csrf()).param("version", "3"))
                .andExpect(status().is3xxRedirection());
        verify(products).hide(10L, 3L);
    }

    @Test
    void imageUploadAndReadUseTrustedContentContract() throws Exception {
        byte[] png = new byte[]{1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("image", "client.png", "image/png", png);
        mvc.perform(multipart("/vendor/products/10/images").file(file)
                        .param("altText", "Preview").with(user("vendor").roles("VENDOR")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/products/10/edit"));
        ArgumentCaptor<UploadContent> upload = ArgumentCaptor.forClass(UploadContent.class);
        verify(products).addImage(eq(10L), upload.capture(), eq("Preview"));
        assertThat(upload.getValue().originalFilename()).isEqualTo("client.png");

        when(products.readImage(10L, 8L)).thenReturn(new StoredContent(png, "image/png"));
        mvc.perform(get("/vendor/products/10/images/8/content").with(user("vendor").roles("VENDOR")))
                .andExpect(status().isOk()).andExpect(content().bytes(png))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    private static ProductView product() {
        return new ProductView(10L, "Product", "Description", new BigDecimal("150000"),
                4, "ACTIVE", 5L, "Thiết bị", true, 3L, List.of());
    }
}
