package com.uteexpress.governance;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.governance.service.ProductModerationService;
import com.uteexpress.governance.service.ShopModerationService;
import com.uteexpress.catalog.service.CatalogQueryService;
import com.uteexpress.catalog.service.ProductService;
import com.uteexpress.catalog.dto.ProductCreateRequest;
import com.uteexpress.shop.service.VendorShopQueryService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Testcontainers
class ModerationIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired ProductModerationService products;
    @Autowired ShopModerationService shops;
    @Autowired CatalogQueryService catalog;
    @Autowired ProductService vendorProducts;
    @Autowired VendorShopQueryService vendorShops;
    @Autowired JdbcTemplate jdbc;
    Long actor, owner, shop, product;

    @BeforeEach void setup() {
        String suffix = Long.toString(System.nanoTime());
        actor = account("actor" + suffix);
        owner = account("owner" + suffix);
        jdbc.update("INSERT INTO uteexpress.user_roles (user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code IN ('USER','VENDOR')", owner);
        shop = jdbc.queryForObject("""
            INSERT INTO uteexpress.shops (owner_id,name,slug,pickup_address,status)
            VALUES (?, 'Moderation shop', ?, 'Address', 'APPROVED') RETURNING id
            """, Long.class, owner, "shop-" + suffix);
        Long category = jdbc.queryForObject("""
            INSERT INTO uteexpress.categories (name,slug) VALUES ('Moderation category', ?) RETURNING id
            """, Long.class, "category-" + suffix);
        product = jdbc.queryForObject("""
            INSERT INTO uteexpress.products (shop_id,category_id,name,price,stock,status)
            VALUES (?, ?, 'Moderation product', 10000, 5, 'ACTIVE') RETURNING id
            """, Long.class, shop, category);
        authenticate(actor, "ADMIN");
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void hideAndRestoreAreAuditedAndAffectPurchasability() {
        assertThat(products.search("Moderation product", 0).getContent()).extracting("id").contains(product);
        assertThat(shops.search("Moderation shop", 0).getContent()).extracting("id").contains(shop);
        assertThat(catalog.requirePurchasableProducts(Set.of(product))).hasSize(1);
        var hidden = products.change(product, 0L, true, "  Policy violation  ");
        assertThat(hidden.status()).isEqualTo("MODERATED");
        assertThat(hidden.reason()).isEqualTo("Policy violation");
        assertThatThrownBy(() -> catalog.requirePurchasableProducts(Set.of(product))).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> products.change(product, 0L, false, "Reviewed")).isInstanceOf(ApplicationException.class);
        authenticate(actor, "MANAGER");
        products.change(product, hidden.version(), false, "Reviewed");
        assertThat(catalog.requirePurchasableProducts(Set.of(product))).hasSize(1);
        assertThat(auditCount("PRODUCT", product)).isEqualTo(2);
    }

    @Test void vendorHiddenProductCannotBeRestoredByOps() {
        jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", product);
        assertThatThrownBy(() -> products.change(product, 0L, false, "Reviewed")).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> products.change(product, 0L, true, "Violation")).isInstanceOf(ApplicationException.class);
        assertThat(auditCount("PRODUCT", product)).isZero();
    }

    @Test void suspendAndResumePreserveRolesAndBlockVendorAccessAndCatalog() {
        var suspended = shops.change(shop, 0L, true, "Policy violation");
        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThatThrownBy(() -> vendorShops.requireApprovedOwnedShop(owner)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> catalog.requirePurchasableProducts(Set.of(product))).isInstanceOf(ApplicationException.class);
        authenticate(owner, "VENDOR");
        var request = new ProductCreateRequest();
        request.setName("Another product");
        request.setPrice(new java.math.BigDecimal("10000"));
        request.setStock(1);
        request.setCategoryId(jdbc.queryForObject("SELECT category_id FROM uteexpress.products WHERE id=?", Long.class, product));
        assertThatThrownBy(() -> vendorProducts.create(request)).isInstanceOf(ApplicationException.class);
        authenticate(actor, "ADMIN");
        shops.change(shop, suspended.version(), false, "Reviewed");
        assertThat(vendorShops.requireApprovedOwnedShop(owner).shopId()).isEqualTo(shop);
        assertThat(catalog.requirePurchasableProducts(Set.of(product))).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT token_version FROM uteexpress.users WHERE id=?", Long.class, owner)).isZero();
        assertThat(jdbc.queryForList("SELECT r.code FROM uteexpress.roles r JOIN uteexpress.user_roles u ON u.role_id=r.id WHERE u.user_id=?", String.class, owner))
                .containsExactlyInAnyOrder("USER", "VENDOR");
        assertThat(auditCount("SHOP", shop)).isEqualTo(2);
    }

    @Test void pendingShopAndInvalidReasonsAndStaleVersionsCannotMutate() {
        for (String reason : List.of("", "   ", "x".repeat(1001))) {
            assertThatThrownBy(() -> shops.change(shop, 0L, true, reason)).isInstanceOf(ApplicationException.class);
            assertThatThrownBy(() -> products.change(product, 0L, true, reason)).isInstanceOf(ApplicationException.class);
        }
        assertThatThrownBy(() -> shops.change(shop, 9L, true, "Violation")).isInstanceOf(ApplicationException.class);
        jdbc.update("UPDATE uteexpress.shops SET status='PENDING' WHERE id=?", shop);
        assertThatThrownBy(() -> shops.change(shop, 0L, true, "Violation")).isInstanceOf(ApplicationException.class);
        assertThat(auditCount("SHOP", shop)).isZero();
    }

    @Test void auditFailureRollsBackBothModerationCommands() {
        authenticate(Long.MAX_VALUE, "ADMIN");
        assertThatThrownBy(() -> products.change(product, 0L, true, "Violation")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> shops.change(shop, 0L, true, "Violation")).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.products WHERE id=?", String.class, product)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shops WHERE id=?", String.class, shop)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.products WHERE id=?", Long.class, product)).isZero();
    }

    @Test void nonOpsRolesCannotSearchOrMutateAtServiceBoundary() {
        for (String role : List.of("USER", "VENDOR", "SHIPPER")) {
            authenticate(actor, role);
            assertThatThrownBy(() -> products.search("", 0)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> shops.search("", 0)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> products.change(product, 0L, true, "Violation")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> shops.change(shop, 0L, true, "Violation")).isInstanceOf(AccessDeniedException.class);
        }
    }

    private int auditCount(String type, Long id) {
        return jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type=? AND target_id=?", Integer.class, type, id);
    }

    private Long account(String unique) {
        return jdbc.queryForObject("""
            INSERT INTO uteexpress.users (email,normalized_email,username,normalized_username,password_hash,status)
            VALUES (?, ?, ?, ?, 'not-a-password', 'ACTIVE') RETURNING id
            """, Long.class, unique+"@example.test", unique+"@example.test", unique, unique);
    }

    private void authenticate(Long id, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var principal = new UteExpressPrincipal(id, "actor", null, 0, authorities, true);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }
}
