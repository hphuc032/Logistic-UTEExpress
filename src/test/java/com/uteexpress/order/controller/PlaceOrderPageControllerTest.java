package com.uteexpress.order.controller;

import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.dto.PlaceOrderResult;
import com.uteexpress.order.service.OrderPlacementService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PlaceOrderPageControllerTest {
    private final OrderPlacementService placement = mock(OrderPlacementService.class);
    private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    private MockMvc mvc;

    @BeforeEach void setUp() {
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new PlaceOrderPageController(placement)).setValidator(validator).build();
    }

    @AfterEach void tearDown() { validator.close(); }

    @Test void sparseItemIndexIsControlledValidationFailureBeforePlacement() throws Exception {
        mvc.perform(post("/user/checkout/view/place-order")
                .param("checkoutKey", "key").param("addressId", "2").param("shippingProviderId", "3")
                .param("shippingServiceCode", "STANDARD").param("paymentMethod", "COD")
                .param("items[1].productId", "10").param("items[1].quantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/checkout/view"))
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.notNullValue()));
        verifyNoInteractions(placement);
    }

    @Test void contiguousItemIndexStillReachesPlacementWithCopiedSelections() throws Exception {
        when(placement.placeOrder(any())).thenReturn(new PlaceOrderResult(
                1L, "ORD-test", OrderStatus.NEW, new BigDecimal("267000.00"), Instant.EPOCH, false));

        mvc.perform(post("/user/checkout/view/place-order")
                .param("checkoutKey", "key").param("addressId", "2").param("shippingProviderId", "3")
                .param("shippingServiceCode", "STANDARD").param("paymentMethod", "COD")
                .param("items[0].productId", "10").param("items[0].quantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/checkout/view"))
                .andExpect(flash().attributeExists("placedOrder"));

        var captor = org.mockito.ArgumentCaptor.forClass(com.uteexpress.checkout.dto.CheckoutRequest.class);
        verify(placement).placeOrder(captor.capture());
        assertThat(captor.getValue().items()).containsExactly(new com.uteexpress.checkout.dto.CheckoutRequest.Item(10L, 2));
    }
}
