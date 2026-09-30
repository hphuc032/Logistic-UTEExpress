package com.uteexpress.engagement;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.engagement.service.EngagementService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class EngagementIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired EngagementService engagement;
    @Autowired MockMvc mvc;
    Long buyer, other, shop, category, product;

    @BeforeEach void setup() {
        String suffix = Long.toString(System.nanoTime());
        buyer = account("buyer" + suffix);
        other = account("other" + suffix);
        shop = jdbc.queryForObject("""
                INSERT INTO uteexpress.shops (owner_id,name,slug,pickup_address,status)
                VALUES (?, 'Engagement shop', ?, 'Address', 'APPROVED') RETURNING id
                """, Long.class, buyer, "eng-shop-" + suffix);
        category = jdbc.queryForObject("""
                INSERT INTO uteexpress.categories (name,slug) VALUES ('Engagement category', ?) RETURNING id
                """, Long.class, "eng-cat-" + suffix);
        product = createProduct("Engagement product", "ACTIVE");
        authenticate(buyer, "USER");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void favoriteIsIdempotentPerUserAndUnavailableProductCannotBeAdded() {
        engagement.addFavorite(product);
        engagement.addFavorite(product);
        assertThat(engagement.favorites()).extracting("productId").containsExactly(product);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.favorites WHERE user_id=?", Integer.class, buyer)).isEqualTo(1);
        authenticate(other, "USER");
        assertThat(engagement.favorites()).isEmpty();
        engagement.addFavorite(product);
        authenticate(buyer, "USER");
        engagement.removeFavorite(product);
        engagement.removeFavorite(product);
        assertThat(engagement.favorites()).isEmpty();
        authenticate(other, "USER");
        assertThat(engagement.favorites()).hasSize(1);
        jdbc.update("UPDATE uteexpress.products SET status='MODERATED', moderation_reason='Policy' WHERE id=?", product);
        assertThatThrownBy(() -> engagement.addFavorite(product)).isInstanceOf(ApplicationException.class);
        assertThat(engagement.favorites().getFirst().available()).isFalse();
    }

    @Test void viewsRefreshTimestampAndKeepOnlyThirtyPerUser() {
        engagement.recordView(product);
        for (int i = 0; i < 31; i++) engagement.recordView(createProduct("Viewed " + i, "ACTIVE"));
        assertThat(engagement.recent()).hasSize(30);
        assertThat(engagement.recent()).extracting("productId").doesNotContain(product);
        engagement.recordView(product);
        assertThat(engagement.recent()).hasSize(30);
        assertThat(engagement.recent().getFirst().productId()).isEqualTo(product);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.product_views WHERE user_id=?", Integer.class, buyer)).isEqualTo(30);
        authenticate(other, "USER");
        assertThat(engagement.recent()).isEmpty();
    }

    @Test void detailRecordsAuthenticatedViewAndHttpRequiresCsrfAndBuyerRole() throws Exception {
        var buyerPrincipal = principal(buyer, "USER");
        mvc.perform(get("/products/{id}", product).with(user(buyerPrincipal)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.product_views WHERE user_id=? AND product_id=?",
                Integer.class, buyer, product)).isEqualTo(1);
        mvc.perform(post("/user/favorites/{id}", product).with(user(buyerPrincipal)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/favorites/{id}", product).with(user(buyerPrincipal)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        authenticate(buyer, "USER");
        assertThat(engagement.isFavorite(product)).isTrue();
        mvc.perform(get("/user/favorites").with(user(principal(other, "SHIPPER"))))
                .andExpect(status().isForbidden());
        authenticate(other, "SHIPPER");
        assertThatThrownBy(() -> engagement.addFavorite(product)).isInstanceOf(AccessDeniedException.class);
    }

    private Long account(String unique) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.users (email,normalized_email,username,normalized_username,password_hash,status)
                VALUES (?, ?, ?, ?, 'not-a-password', 'ACTIVE') RETURNING id
                """, Long.class, unique+"@example.test", unique+"@example.test", unique, unique);
    }

    private Long createProduct(String name, String status) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.products (shop_id,category_id,name,price,stock,status)
                VALUES (?, ?, ?, 10000, 5, ?) RETURNING id
                """, Long.class, shop, category, name, status);
    }

    private UteExpressPrincipal principal(Long id, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        return new UteExpressPrincipal(id, "engagement", null, 0, authorities, true);
    }

    private void authenticate(Long id, String role) {
        var principal = principal(id, role);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
