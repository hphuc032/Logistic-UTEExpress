package com.uteexpress.ui;

import com.uteexpress.catalog.dto.PublicHomeView;
import com.uteexpress.catalog.service.PublicCatalogService;
import com.uteexpress.identity.service.RegistrationService;
import com.uteexpress.identity.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

import com.uteexpress.security.RoleCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(UiLayoutTest.UiFixtureController.class)
class UiLayoutTest {
    @MockitoBean com.uteexpress.governance.service.ProductModerationService productModerationService;
    @MockitoBean com.uteexpress.governance.service.ShopModerationService shopModerationService;
    @MockitoBean com.uteexpress.catalog.service.ProductService productService;
    @MockitoBean PublicCatalogService publicCatalogService;
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
    @MockitoBean com.uteexpress.promotion.service.VoucherService voucherService;
    @MockitoBean com.uteexpress.promotion.service.VendorVoucherService vendorVoucherService;
    @MockitoBean com.uteexpress.catalog.service.ProductPromotionManagementService productPromotionManagementService;
    @MockitoBean com.uteexpress.promotion.service.PromotionManagementService promotionManagementService;
    @MockitoBean com.uteexpress.order.service.OrderPlacementService orderPlacementService;
    @MockitoBean com.uteexpress.order.service.BuyerOrderService buyerOrderService;
    @MockitoBean com.uteexpress.order.service.VendorOrderService vendorOrderService;
    @MockitoBean com.uteexpress.order.service.VendorOrderAuthority vendorOrderAuthority;
    @MockitoBean com.uteexpress.order.service.ReturnOrderAuthority returnOrderAuthority;
    @MockitoBean com.uteexpress.order.service.RefundOrderAuthority refundOrderAuthority;
    @MockitoBean com.uteexpress.payment.service.ManualCodRefundService manualCodRefundService;
    @MockitoBean com.uteexpress.order.service.ReturnQueryService returnQueryService;
    @MockitoBean com.uteexpress.payment.service.PaymentReadService paymentReadService;
    @MockitoBean com.uteexpress.payment.service.PaymentService paymentService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean com.uteexpress.shipping.service.ShippingConfigService shippingConfig;
    @MockitoBean com.uteexpress.shipping.service.ShippingQuoteService shippingQuote;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerificationService;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordResetService;
    @MockitoBean com.uteexpress.governance.service.CategoryService categoryService;
    @MockitoBean
    private com.uteexpress.governance.service.AuditLogService auditLogService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private UserRoleRepository userRoleRepository;

    @MockitoBean
    private com.uteexpress.identity.service.IdentityAuthenticationService identityAuthenticationService;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @BeforeEach
    void publicHome() {
        when(publicCatalogService.home()).thenReturn(new PublicHomeView(List.of(), List.of(), List.of()));
    }

    @Test
    void landingPageRendersSharedLayoutForAnonymousUser() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("UTEExpress")))
                .andExpect(content().string(containsString("id=\"main-navigation\"")))
                .andExpect(content().string(containsString("id=\"site-footer\"")))
                .andExpect(content().string(containsString("/css/app.css")))
                .andExpect(content().string(containsString("/js/app.js")))
                .andExpect(content().string(containsString("bootstrap@5.3.3")));
    }

    @Test
    void localStylesheetIsPublic() throws Exception {
        mvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString("--ute-navy-900")));
    }

    @Test
    void localJavascriptIsPublicAndUsesSafeTextAssignment() throws Exception {
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("textContent")))
                .andExpect(content().string(not(containsString("innerHTML"))));
    }

    @Test
    void flashMessageEscapesUserControlledMarkup() throws Exception {
        mvc.perform(get("/test-fixtures/ui/escaped-alert").with(user("ui-test")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))));
    }

    @Test
    void errorPageContainsNoInternalFailureDetails() throws Exception {
        mvc.perform(get("/test-fixtures/ui/error-page").with(user("ui-test")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không thể xử lý yêu cầu lúc này.")))
                .andExpect(content().string(not(containsString("stacktrace"))))
                .andExpect(content().string(not(containsString("java.lang"))))
                .andExpect(content().string(not(containsString("SQLException"))));
    }

    @Test
    void foundationAndHealthEndpointsRemainPublic() throws Exception {
        mvc.perform(get("/api/v1/foundation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FOUNDATION_READY"));

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "VENDOR", "MANAGER", "ADMIN", "SHIPPER"})
    void navigationShowsOnlyAvailableRoleWorkspacesAndLinksToExistingPages(String role) throws Exception {
        String html = mvc.perform(get("/").with(user(principal(role))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertWorkspace(html, "vendor", role.equals("VENDOR"));
        assertWorkspace(html, "manager", role.equals("MANAGER"));
        assertWorkspace(html, "admin", role.equals("ADMIN"));
        assertWorkspace(html, "shipper", role.equals("SHIPPER"));
        boolean buyer = Set.of("USER", "VENDOR").contains(role);
        assertThat(html.contains("id=\"buyer-menu\"")).isEqualTo(buyer);
        if (buyer) {
            assertThat(html).contains("href=\"/user/checkout/view\"", "href=\"/user/shop\"",
                    "href=\"/user/profile\"", "href=\"/orders\"");
        }
        if (role.equals("USER")) assertThat(html).contains("href=\"/user/shop/register\"");
        if (role.equals("VENDOR")) assertThat(html).contains("href=\"/vendor/dashboard\"",
                "href=\"/vendor/products/new\"", "href=\"/vendor/vouchers/new\"");
        if (Set.of("ADMIN", "MANAGER").contains(role)) {
            String prefix = "/" + role.toLowerCase(java.util.Locale.ROOT);
            for (String page : List.of("/orders", "/shippers", "/shipping/providers", "/shipping/rates", "/commissions")) {
                assertThat(html).contains("href=\"" + prefix + page + "\"");
            }
        }
        if (role.equals("MANAGER")) assertThat(html).doesNotContain("href=\"/admin/");
        if (role.equals("ADMIN")) assertThat(html).contains("href=\"/admin/vouchers\"",
                "href=\"/admin/promotions\"", "href=\"/admin/dashboard/revenue\"");
        assertThat(html).contains("action=\"/logout\"", "name=\"_csrf\"");
        assertAllNavigationLinksHaveGetHandlers(html);
    }

    @Test
    void multipleRolesKeepEachWorkspaceAvailableWithoutLeakingUsernameMarkup() throws Exception {
        String html = mvc.perform(get("/").with(user(principal("USER", "VENDOR", "MANAGER", "ADMIN", "SHIPPER"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String role : List.of("buyer", "vendor", "manager", "admin", "shipper")) {
            assertThat(html).contains("id=\"" + role + "-menu\"");
        }
        assertThat(html).contains("&lt;nav-user&gt;").doesNotContain("<nav-user>");
        assertAllNavigationLinksHaveGetHandlers(html);
    }

    @Test
    void anonymousNavigationKeepsRoleMenusPrivateAndOffersEmailVerificationFromLogin() throws Exception {
        String html = mvc.perform(get("/")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("href=\"/login\"", "href=\"/register\"");
        for (String role : List.of("buyer", "vendor", "manager", "admin", "shipper", "account")) {
            assertThat(html).doesNotContain("id=\"" + role + "-menu\"");
        }
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/verify-otp\"")));
    }

    @Test
    void navigationDoesNotChangeEndpointAuthorizationOrLogoutCsrf() throws Exception {
        mvc.perform(get("/manager/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/manager/orders").with(user(principal("USER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/commissions").with(user(principal("MANAGER"))))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/logout")
                        .with(user(principal("USER"))))
                .andExpect(status().isForbidden());
    }

    private void assertWorkspace(String html, String role, boolean present) {
        assertThat(html.contains("id=\"" + role + "-menu\"")).isEqualTo(present);
    }

    private UteExpressPrincipal principal(String... roles) {
        return new UteExpressPrincipal(42L, "<nav-user>", null, 0L,
                Arrays.stream(roles).map(RoleCode::valueOf)
                        .map(code -> new org.springframework.security.core.authority.SimpleGrantedAuthority(code.authority()))
                        .toList(), true);
    }

    private void assertAllNavigationLinksHaveGetHandlers(String html) {
        String navigation = html.substring(html.indexOf("<nav"), html.indexOf("</nav>") + 6);
        var links = Pattern.compile("href=\"(/[^\"]*)\"").matcher(navigation);
        while (links.find()) {
            String path = links.group(1);
            boolean found = requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                    .filter(info -> info.getMethodsCondition().getMethods().isEmpty()
                            || info.getMethodsCondition().getMethods().contains(RequestMethod.GET))
                    .flatMap(info -> info.getPatternValues().stream())
                    .anyMatch(pattern -> new PathPatternParser().parse(pattern).matches(PathContainer.parsePath(path)));
            assertThat(found).as("Navigation target has an existing GET handler: %s", path).isTrue();
        }
    }

    @Controller
    static class UiFixtureController {
    @MockitoBean com.uteexpress.identity.service.VendorRoleGrantService vendorRoleGrantService;
        @GetMapping("/test-fixtures/ui/escaped-alert")
        String escapedAlert(Model model) {
            model.addAttribute("successMessage", "<script>alert('xss')</script>");
            return "index";
        }

        @GetMapping("/test-fixtures/ui/error-page")
        String errorPage() {
            return "error/500";
        }
    }
}
