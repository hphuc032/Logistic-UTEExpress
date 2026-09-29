package com.uteexpress.catalog.controller;

import com.uteexpress.catalog.dto.ProductCard;
import com.uteexpress.catalog.dto.ProductDetailView;
import com.uteexpress.catalog.dto.ProductImageView;
import com.uteexpress.catalog.dto.ProductSearchCriteria;
import com.uteexpress.catalog.dto.ProductSearchPage;
import com.uteexpress.catalog.dto.PublicCategorySummary;
import com.uteexpress.catalog.dto.PublicCategoryView;
import com.uteexpress.catalog.dto.PublicHomeView;
import com.uteexpress.catalog.dto.PublicShopSummary;
import com.uteexpress.catalog.dto.PublicShopView;
import com.uteexpress.catalog.service.PublicCatalogService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.StoredContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PublicCatalogControllerTest {
    @MockitoBean PublicCatalogService catalog;
    @MockitoBean com.uteexpress.governance.service.ProductModerationService productModerationService;
    @MockitoBean com.uteexpress.governance.service.ShopModerationService shopModerationService;
    @MockitoBean com.uteexpress.catalog.service.ProductService productService;
    @MockitoBean com.uteexpress.catalog.service.InventoryService inventoryService;
    @MockitoBean com.uteexpress.governance.service.CategoryQueryService categoryQueryService;
    @MockitoBean com.uteexpress.shop.service.VendorShopQueryService vendorShopQueryService;
    @MockitoBean com.uteexpress.identity.service.VendorRoleGrantService vendorRoleGrantService;
    @MockitoBean com.uteexpress.shop.service.ShopApprovalService shopApprovalService;
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.governance.service.AccountGovernanceService accountGovernanceService;
    @MockitoBean com.uteexpress.identity.service.IdentityAccountGovernanceService identityAccountGovernanceService;
    @MockitoBean com.uteexpress.account.service.AddressService addressService;
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerificationService;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordResetService;
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryService;
    @MockitoBean com.uteexpress.governance.service.AuditLogService auditLogService;
    @MockitoBean com.uteexpress.identity.service.RegistrationService registrationService;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoleRepository;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identityAuthenticationService;

    @Autowired MockMvc mvc;

    ProductCard card;
    ProductDetailView detail;

    @BeforeEach
    void fixture() {
        card = new ProductCard(11L, "Hộp giao hàng", new BigDecimal("125000"), 4,
                "shop-a", "Shop A", "dong-goi", "Đóng gói", 21L);
        detail = new ProductDetailView(11L, card.name(), "Mô tả an toàn", card.price(), card.stock(),
                card.shopSlug(), card.shopName(), card.categorySlug(), card.categoryName(),
                List.of(new ProductImageView(21L, 0, "Ảnh sản phẩm")));
        when(catalog.home()).thenReturn(new PublicHomeView(List.of(card),
                List.of(new PublicCategorySummary("dong-goi", "Đóng gói")),
                List.of(new PublicShopSummary("shop-a", "Shop A", "Cửa hàng mẫu"))));
        when(catalog.search(any())).thenReturn(new ProductSearchPage(List.of(card),
                new ProductSearchCriteria(), 1, 0, 12, 1, false, false));
        when(catalog.product(11L)).thenReturn(detail);
        when(catalog.category("dong-goi")).thenReturn(
                new PublicCategoryView("dong-goi", "Đóng gói", List.of(card)));
        when(catalog.shops()).thenReturn(List.of(new PublicShopSummary("shop-a", "Shop A", "Cửa hàng mẫu")));
        when(catalog.categories()).thenReturn(List.of(new PublicCategorySummary("dong-goi", "Đóng gói")));
        when(catalog.shop("shop-a")).thenReturn(new PublicShopView("shop-a", "Shop A", "Cửa hàng mẫu", List.of(card)));
    }

    @Test
    void anonymousGuestCanBrowseEveryPublicPage() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(view().name("index"))
                .andExpect(content().string(containsString("Hộp giao hàng")));
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(view().name("products/list"));
        mvc.perform(get("/products/11")).andExpect(status().isOk()).andExpect(view().name("products/detail"));
        mvc.perform(get("/categories/dong-goi")).andExpect(status().isOk())
                .andExpect(view().name("categories/detail"));
        mvc.perform(get("/shops")).andExpect(status().isOk()).andExpect(view().name("shops/list"));
        mvc.perform(get("/shops/shop-a")).andExpect(status().isOk()).andExpect(view().name("shops/detail"));
    }

    @Test
    void vendorControlledTextIsEscapedInProductDetail() throws Exception {
        String attack = "<script>alert('catalog')</script>";
        ProductDetailView malicious = new ProductDetailView(12L, attack, attack, new BigDecimal("10000"), 1,
                "shop-a", attack, "dong-goi", attack, List.of(new ProductImageView(22L, 0, attack)));
        when(catalog.product(12L)).thenReturn(malicious);

        mvc.perform(get("/products/12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString(attack))));
    }

    @Test
    void publicImageUsesTrustedMetadataAndDefensiveHeaders() throws Exception {
        when(catalog.image(11L, 21L)).thenReturn(new StoredContent(new byte[]{1, 2, 3}, "image/png"));

        mvc.perform(get("/products/11/images/21/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void nonPublicDetailAndMediaUseSafe404Contract() throws Exception {
        when(catalog.product(99L)).thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        when(catalog.image(99L, 1L)).thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get("/products/99")).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString("MODERATED"))));
        mvc.perform(get("/products/99/images/1/content")).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("storage"))));
    }

    @Test
    void paginationLinksPreserveDiscoveryParametersAndExcludeBlockedTopRatedSort() throws Exception {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setQ("Laptop");
        criteria.setShop("shop-a");
        criteria.setCategory("dong-goi");
        criteria.setMinPrice(new BigDecimal("100000"));
        criteria.setMaxPrice(new BigDecimal("500000"));
        criteria.setSort("priceAsc");
        criteria.setSize(12);
        when(catalog.search(any())).thenReturn(new ProductSearchPage(List.of(card), criteria,
                25, 0, 12, 3, false, true));

        mvc.perform(get("/products")
                        .param("q", "Laptop")
                        .param("shop", "shop-a")
                        .param("category", "dong-goi")
                        .param("minPrice", "100000")
                        .param("maxPrice", "500000")
                        .param("sort", "priceAsc")
                        .param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("q=Laptop")))
                .andExpect(content().string(containsString("shop=shop-a")))
                .andExpect(content().string(containsString("category=dong-goi")))
                .andExpect(content().string(containsString("minPrice=100000")))
                .andExpect(content().string(containsString("maxPrice=500000")))
                .andExpect(content().string(containsString("sort=priceAsc")))
                .andExpect(content().string(containsString("size=12")))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(not(containsString("value=\"topRated\""))));
    }

    @Test
    void invalidPriceAndPaginationInputsRenderValidationWithoutQueryingDatabase() throws Exception {
        for (String[] parameters : new String[][]{
                {"minPrice", "-1"}, {"maxPrice", "-1"}, {"minPrice", "1.5"},
                {"size", "0"}, {"size", "-100"}, {"size", "999999999"}}) {
            mvc.perform(get("/products").param(parameters[0], parameters[1]))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("is-invalid")));
        }
        mvc.perform(get("/products").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("app-empty-state")));
        mvc.perform(get("/products").param("minPrice", "300000").param("maxPrice", "200000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá tối thiểu không được lớn hơn giá tối đa.")));
        verify(catalog, never()).search(any());
    }

    @Test
    void searchQueryIsEscapedWhenRedisplayed() throws Exception {
        String attack = "<script>alert(1)</script>";
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setQ(attack);
        when(catalog.search(any())).thenReturn(new ProductSearchPage(List.of(), criteria,
                0, 0, 12, 0, false, false));

        mvc.perform(get("/products").param("q", attack))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("value=\"" + attack + "\""))))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")));
    }
}
