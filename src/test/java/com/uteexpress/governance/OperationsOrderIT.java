package com.uteexpress.governance;

import com.uteexpress.governance.service.OperationsOrderService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class OperationsOrderIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired OperationsOrderService orders;

    @Test void opsCanFilterAndReadPersistedFactsWhileUserIsDenied() throws Exception {
        Long buyer = createUser();
        Long vendor = createUser();
        Long shop = jdbc.queryForObject("""
                INSERT INTO uteexpress.shops (owner_id, name, slug, pickup_address, status)
                VALUES (?, 'Ops shop', ?, 'Pickup', 'APPROVED') RETURNING id
                """, Long.class, vendor, "ops-" + System.nanoTime());
        String code = "OPS-" + System.nanoTime();
        Long order = jdbc.queryForObject("""
                INSERT INTO uteexpress.orders (order_code, checkout_key, request_hash, buyer_id, shop_id,
                    status, receiver_name, phone, province_code, district, detail, subtotal,
                    discount_total, shipping_fee, grand_total, commission_amount, commission_rate_snapshot)
                VALUES (?, ?, 'secret-request-hash', ?, ?, 'CONFIRMED', 'Ops buyer', '0123456789',
                    'HCM', 'District', 'Street', 100, 0, 10, 110, 0, 0) RETURNING id
                """, Long.class, code, code, buyer, shop);
        jdbc.update("UPDATE uteexpress.orders SET shipping_provider_id=42, shipping_service_code='STANDARD' WHERE id=?", order);
        jdbc.update("""
                INSERT INTO uteexpress.order_status_history (order_id, from_status, to_status, actor_id)
                VALUES (?, 'NEW', 'CONFIRMED', ?)
                """, order, vendor);
        jdbc.update("""
                INSERT INTO uteexpress.payments (order_id, method, status, amount, attempt_key)
                VALUES (?, 'COD', 'UNPAID', 110, ?)
                """, order, "ops-pay-" + order);

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal(buyer, "ADMIN"), null, principal(buyer, "ADMIN").getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var filtered = orders.search(code, "CONFIRMED", 0, 20);
        assertThat(filtered.content()).hasSize(1);
        assertThat(filtered.content().getFirst().id()).isEqualTo(order);
        assertThat(orders.search(code, "NEW", 0, 20).content()).isEmpty();
        assertThat(orders.search("%", null, 0, 20).content()).isEmpty();
        var detail = orders.detail(order);
        assertThat(detail.buyerId()).isEqualTo(buyer);
        assertThat(detail.facts().timeline()).hasSize(1);
        assertThat(detail.facts().payments()).hasSize(1);
        assertThat(detail.facts().shippingProviderId()).isEqualTo(42L);
        assertThat(detail.facts().shippingServiceCode()).isEqualTo("STANDARD");

        SecurityContextHolder.clearContext();
        mvc.perform(get("/admin/orders").with(user(principal(buyer, "ADMIN")))
                        .param("query", code).accept("text/html"))
                .andExpect(status().isOk()).andExpect(view().name("governance/orders/list"));
        mvc.perform(get("/manager/orders/{id}", order).with(user(principal(buyer, "MANAGER")))
                        .accept("application/json"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.facts.orderCode").value(code))
                .andExpect(jsonPath("$.facts.shippingServiceCode").value("STANDARD"))
                .andExpect(jsonPath("$.facts.payments[0].status").value("UNPAID"))
                .andExpect(jsonPath("$.facts.checkoutKey").doesNotExist())
                .andExpect(jsonPath("$.facts.requestHash").doesNotExist());
        mvc.perform(get("/manager/orders").with(user(principal(buyer, "USER")))
                        .accept("application/json"))
                .andExpect(status().isForbidden());
    }

    private Long createUser() {
        String name = "ops" + System.nanoTime();
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.users (email, normalized_email, username, normalized_username,
                    password_hash, status) VALUES (?, ?, ?, ?, 'not-a-password', 'ACTIVE') RETURNING id
                """, Long.class, name + "@example.test", name + "@example.test", name, name);
    }

    private UteExpressPrincipal principal(Long id, String role) {
        return new UteExpressPrincipal(id, "ops", null, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), true);
    }
}
