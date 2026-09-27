package com.uteexpress.shipping;

import com.uteexpress.shipping.dto.*;
import com.uteexpress.shipping.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc
class ShippingConfigWebTest {
    @MockitoBean com.uteexpress.identity.service.AccountIdentityService accountIdentityService;
    @MockitoBean com.uteexpress.cart.service.CartService cartService;
    @MockitoBean com.uteexpress.shop.service.ShopRegistrationService shopRegistrationService;
    @MockitoBean ShippingConfigService config;
    @MockitoBean ShippingQuoteService quotes;
    @MockitoBean com.uteexpress.identity.service.EmailVerificationService emailVerificationService;
    @MockitoBean com.uteexpress.identity.service.PasswordResetService passwordResetService;
    @MockitoBean com.uteexpress.governance.service.CategoryService categories;
    @MockitoBean com.uteexpress.governance.service.AuditLogService audit;
    @MockitoBean com.uteexpress.identity.service.RegistrationService registration;
    @MockitoBean com.uteexpress.identity.service.IdentityAuthenticationService identities;
    @MockitoBean com.uteexpress.identity.repository.UserRoleRepository userRoles;
    @Autowired MockMvc mvc;

    @Test void roleAndCsrfMatrix() throws Exception {
        for(String ops:List.of("admin","manager")) for(String kind:List.of("providers","rates")) {
            String path="/"+ops+"/shipping/"+kind;
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for(String role:List.of("USER","VENDOR","SHIPPER",ops.equals("admin")?"MANAGER":"ADMIN")) {
                mvc.perform(get(path).with(user("actor").roles(role))).andExpect(status().isForbidden());
                mvc.perform(post(path).with(user("actor").roles(role)).with(csrf())).andExpect(status().isForbidden());
            }
            mvc.perform(post(path).with(user("actor").roles(ops.toUpperCase()))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(config);
    }
    @Test void listsAndFormsRenderForBothRoles() throws Exception {
        var provider=new ShippingProviderView(1L,"TEST","<script>unsafe</script>",true,0L);
        var rate=new ShippingRateView(2L,1L,"Example","STANDARD","DEMO_REGION",new BigDecimal("30000.00"),true,0L);
        when(config.providers(0)).thenReturn(new PageImpl<>(List.of(provider),PageRequest.of(0,20),1));
        when(config.rates(0)).thenReturn(new PageImpl<>(List.of(rate),PageRequest.of(0,20),1));
        when(config.activeProviders()).thenReturn(List.of(provider));
        when(config.provider(1L)).thenReturn(provider);
        when(config.rate(2L)).thenReturn(rate);
        for(String ops:List.of("admin","manager")) {
            for(String route:List.of("providers","rates","providers/new","rates/new","providers/1/edit","rates/2/edit")) {
                mvc.perform(get("/"+ops+"/shipping/"+route).with(user("actor").roles(ops.toUpperCase())))
                        .andExpect(status().isOk())
                        .andExpect(content().string(not(containsString("<script>unsafe</script>"))))
                        .andExpect(content().string(not(containsString("3E+4"))))
                        .andExpect(content().string(containsString("name=\"_csrf\"")))
                        .andDo(r -> {
                            var path=java.nio.file.Path.of("target","ui-preview","shipping-"+ops+"-"+route.replace('/','-')+".html");
                            java.nio.file.Files.createDirectories(path.getParent());
                            java.nio.file.Files.writeString(path,r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                        });
            }
        }
    }
    @Test void invalidOrFractionalFeeStaysOnForm() throws Exception {
        when(config.activeProviders()).thenReturn(List.of());
        for(String fee:List.of("-1","0.5","100000000000000000")) {
            mvc.perform(post("/admin/shipping/rates").with(user("actor").roles("ADMIN")).with(csrf())
                    .param("providerId","1").param("serviceCode","STANDARD").param("destinationRegion","DEMO_REGION")
                    .param("fee",fee).param("active","true"))
                    .andExpect(status().isOk()).andExpect(model().hasErrors()).andExpect(view().name("shipping/rate-form"));
        }
        verify(config,never()).createRate(any());
    }
    @Test void creationPassesOnlyAllowedConfigurationFields() throws Exception {
        mvc.perform(post("/manager/shipping/providers").with(user("actor").roles("MANAGER")).with(csrf())
                .param("code","DEMO").param("name","Demo").param("active","true").param("actorId","999"))
                .andExpect(redirectedUrl("/manager/shipping/providers"));
        verify(config).createProvider(new ShippingProviderRequest("DEMO","Demo",true,null));
        mvc.perform(post("/admin/shipping/rates").with(user("actor").roles("ADMIN")).with(csrf())
                .param("providerId","1").param("serviceCode","STANDARD").param("destinationRegion","DEMO_REGION")
                .param("fee","30000.00").param("active","true"))
                .andExpect(redirectedUrl("/admin/shipping/rates"));
        verify(config).createRate(new ShippingRateRequest(1L,"STANDARD","DEMO_REGION",new BigDecimal("30000.00"),true,null));
    }
}
