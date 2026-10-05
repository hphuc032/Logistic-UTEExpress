package com.uteexpress.integration;

import com.uteexpress.checkout.dto.CheckoutPreview;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.dto.PlaceOrderResult;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CheckoutFlowIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    void authenticatedBuyerCompletesCatalogToNewOrderFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String username = "int01" + suffix;
        String password = "Integration123!";
        long buyer = createUser(username, password);
        long category = jdbc.queryForObject("""
                INSERT INTO uteexpress.categories(name,slug,active)
                VALUES ('INT-01 Category',?,true) RETURNING id
                """, Long.class, "int-category-" + suffix);
        long shop = jdbc.queryForObject("""
                INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status)
                VALUES (?,'INT-01 Shop',?,'Pickup','APPROVED') RETURNING id
                """, Long.class, buyer, "int-shop-" + suffix);
        long product = jdbc.queryForObject("""
                INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status)
                VALUES (?,?,'INT-01 Product',125000,10,'ACTIVE') RETURNING id
                """, Long.class, shop, category);
        long address = jdbc.queryForObject("""
                INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default)
                VALUES (?,'INT Buyer','0900000000','VN','Thu Duc','UTE address',true) RETURNING id
                """, Long.class, buyer);
        long provider = jdbc.queryForObject("""
                INSERT INTO uteexpress.shipping_providers(code,name,active)
                VALUES (?,'INT Shipping',true) RETURNING id
                """, Long.class, "INT" + suffix.toUpperCase());
        jdbc.update("""
                INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active)
                VALUES (?,'STANDARD','VN',17000,true)
                """, provider);
        jdbc.update("""
                INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by)
                VALUES (7,CURRENT_TIMESTAMP - INTERVAL '1 day',?)
                """, buyer);

        Cookie jwt = mvc.perform(post("/login").with(csrf())
                        .param("identifier", username).param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
        assertThat(jwt).isNotNull();

        mvc.perform(get("/products/{id}", product).cookie(jwt))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INT-01 Product")))
                .andExpect(content().string(containsString("/user/cart/view/items")))
                .andExpect(content().string(containsString("Thêm vào giỏ")));

        mvc.perform(post("/user/cart/view/items").cookie(jwt).with(csrf())
                        .param("productId", Long.toString(product)).param("quantity", "2")
                        .param("buyerId", Long.toString(Long.MAX_VALUE)).param("price", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/cart/view"))
                .andExpect(flash().attributeExists("successMessage"));

        mvc.perform(get("/user/cart/view").cookie(jwt))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INT-01 Product")))
                .andExpect(content().string(containsString("Checkout — xem báo giá")));

        var quoteResult = mvc.perform(post("/user/checkout/view").cookie(jwt).with(csrf())
                        .param("addressId", Long.toString(address))
                        .param("shippingProviderId", Long.toString(provider))
                        .param("shippingServiceCode", "STANDARD")
                        .param("subtotal", "1").param("shippingFee", "1").param("grandTotal", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("checkout/quote"))
                .andExpect(content().string(containsString("Đặt hàng")))
                .andReturn();
        CheckoutPreview preview = (CheckoutPreview) quoteResult.getModelAndView().getModel().get("preview");
        String checkoutKey = (String) quoteResult.getModelAndView().getModel().get("placeOrderKey");
        assertThat(preview.quote().totals().grandTotal()).isEqualByComparingTo("267000");
        assertThat(checkoutKey).isNotBlank();

        var placed = mvc.perform(post("/user/checkout/view/place-order").cookie(jwt).with(csrf())
                        .param("checkoutKey", checkoutKey)
                        .param("items[0].productId", Long.toString(product))
                        .param("items[0].quantity", "2")
                        .param("addressId", Long.toString(address))
                        .param("shippingProviderId", Long.toString(provider))
                        .param("shippingServiceCode", "STANDARD")
                        .param("paymentMethod", "COD")
                        .param("buyerId", Long.toString(Long.MAX_VALUE))
                        .param("shopId", Long.toString(Long.MAX_VALUE))
                        .param("grandTotal", "1").param("commissionAmount", "1")
                        .param("status", "DELIVERED").param("paymentStatus", "PAID"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/user/checkout/view"))
                .andExpect(flash().attributeExists("placedOrder"))
                .andReturn();
        PlaceOrderResult receipt = (PlaceOrderResult) placed.getFlashMap().get("placedOrder");
        assertThat(receipt.status()).isEqualTo(OrderStatus.NEW);
        assertThat(receipt.grandTotal()).isEqualByComparingTo("267000");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uteexpress.orders WHERE buyer_id=?", Integer.class, buyer))
                .isEqualTo(1);
        var order = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", receipt.orderId());
        assertThat(order).containsEntry("buyer_id", buyer).containsEntry("shop_id", shop)
                .containsEntry("status", "NEW").containsEntry("detail", "UTE address");
        assertThat((BigDecimal) order.get("subtotal")).isEqualByComparingTo("250000");
        assertThat((BigDecimal) order.get("shipping_fee")).isEqualByComparingTo("17000");
        assertThat((BigDecimal) order.get("grand_total")).isEqualByComparingTo("267000");
        assertThat((BigDecimal) order.get("commission_rate_snapshot")).isEqualByComparingTo("7");
        assertThat((BigDecimal) order.get("commission_amount")).isEqualByComparingTo("17500");
        assertThat(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, product))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uteexpress.cart_items ci JOIN uteexpress.carts c ON c.id=ci.cart_id WHERE c.user_id=?", Integer.class, buyer))
                .isZero();
        assertThat(jdbc.queryForMap("SELECT method,status,amount FROM uteexpress.payments WHERE order_id=?", receipt.orderId()))
                .containsEntry("method", "COD").containsEntry("status", "UNPAID")
                .containsEntry("amount", new BigDecimal("267000.00"));

        mvc.perform(get("/orders").cookie(jwt))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(receipt.orderCode())));
        mvc.perform(get("/orders/{id}", receipt.orderId()).cookie(jwt))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NEW")))
                .andExpect(content().string(containsString("INT-01 Product")))
                .andExpect(content().string(containsString("COD")))
                .andExpect(content().string(containsString("UNPAID")));
    }

    private long createUser(String username, String password) {
        Long id = jdbc.queryForObject("""
                INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,
                                             status,email_verified_at)
                VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, username + "@example.test", username + "@example.test", username, username,
                passwords.encode(password));
        jdbc.update("""
                INSERT INTO uteexpress.user_roles(user_id,role_id)
                SELECT ?,id FROM uteexpress.roles WHERE code='USER'
                """, id);
        return id;
    }
}
