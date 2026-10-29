package com.uteexpress.catalog.controller;

import com.uteexpress.catalog.service.ProductPromotionManagementService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MVC binding regression only; real authentication/CSRF and persistence live in PromotionBrowserFormsIT. */
class VendorProductPromotionBindingTest {
    private ProductPromotionManagementService management;
    private MockMvc mvc;

    @BeforeEach void setup() {
        management = mock(ProductPromotionManagementService.class);
        mvc = MockMvcBuilders.standaloneSetup(new VendorProductPromotionController(management)).build();
    }

    @Test void disableAcceptsNormalFormTransportHeaders() throws Exception {
        mvc.perform(form().param("version", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vendor/products/11/promotions"));
        verify(management).get(11L, 22L);
        verify(management).disable(11L, 22L, 3L);
    }

    @Test void businessVersionComesFromTheFormEvenWhenAHeaderHasTheSameName() throws Exception {
        mvc.perform(form().param("version", "3").header("version", "99"))
                .andExpect(status().is3xxRedirection());
        verify(management).disable(11L, 22L, 3L);
    }

    @Test void aHeaderCannotSupplyTheRequiredVersion() throws Exception {
        mvc.perform(form().header("version", "3")).andExpect(status().isBadRequest());
        verify(management, never()).disable(anyLong(), anyLong(), anyLong());
    }

    @ParameterizedTest @ValueSource(strings = {"id", "productId", "discountPercent", "shopId"})
    void submittedBusinessFieldsRemainRejected(String field) throws Exception {
        mvc.perform(form().param("version", "3").param(field, "11"))
                .andExpect(status().isBadRequest());
        verify(management, never()).disable(anyLong(), anyLong(), anyLong());
    }

    @ParameterizedTest @ValueSource(strings = {"", "-1", "invalid"})
    void invalidVersionsRemainRejected(String version) throws Exception {
        mvc.perform(form().param("version", version)).andExpect(status().isBadRequest());
        verify(management, never()).disable(anyLong(), anyLong(), anyLong());
    }

    @Test void ownedLookupStillRejectsForeignResourcesBeforeMutation() throws Exception {
        when(management.get(11L, 22L)).thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(form().param("version", "3")).andExpect(status().isNotFound());
        verify(management, never()).disable(anyLong(), anyLong(), anyLong());
    }

    private static MockHttpServletRequestBuilder form() {
        return post("/vendor/products/11/promotions/22/disable")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Host", "localhost").header("User-Agent", "QA02 binding regression")
                .header("Origin", "http://localhost").header("Cookie", "fixture=value");
    }
}
