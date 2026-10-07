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
