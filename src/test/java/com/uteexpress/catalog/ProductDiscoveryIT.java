package com.uteexpress.catalog;

import com.uteexpress.catalog.dto.ProductSearchCriteria;
import com.uteexpress.catalog.service.PublicCatalogService;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class ProductDiscoveryIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PublicCatalogService catalog;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE uteexpress.users, uteexpress.categories, uteexpress.shops, "
                + "uteexpress.products, uteexpress.orders RESTART IDENTITY CASCADE");
    }

    @Test
    void schemaRemainsValidAndProd02NeedsNoMigration() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(entityManagerFactory.isOpen()).isTrue();
    }

    @Test
    void nameSearchIsCaseInsensitiveAndTreatsInjectionAndWildcardsAsLiteralText() {
        long category = category("electronics", true);
        long shop = shop("shop-a", "APPROVED");
        product(shop, category, "Gaming Laptop", "100000", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"));
        product(shop, category, "Laptop Stand", "200000", "ACTIVE", Instant.parse("2026-01-02T00:00:00Z"));
        product(shop, category, "Phone Case", "300000", "ACTIVE", Instant.parse("2026-01-03T00:00:00Z"));
        product(shop, category, "100% Safe Case", "400000", "ACTIVE", Instant.parse("2026-01-04T00:00:00Z"));

        assertThat(search(criteria("LAPTOP", null, null, null, null, "newest", 0, 12)))
                .containsExactly("Laptop Stand", "Gaming Laptop");
        assertThat(search(criteria("' OR 1=1 --", null, null, null, null, "newest", 0, 12)))
                .isEmpty();
        assertThat(search(criteria("%", null, null, null, null, "newest", 0, 12)))
                .containsExactly("100% Safe Case");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.products", Integer.class)).isEqualTo(4);
    }

    @Test
    void shopCategoryAndWholeVndPriceFiltersComposeWithAccurateCount() {
        long categoryA = category("category-a", true);
        long categoryB = category("category-b", true);
        long shopA = shop("shop-a", "APPROVED");
        long shopB = shop("shop-b", "APPROVED");
        product(shopA, categoryA, "Laptop 100", "100000", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"));
        product(shopA, categoryA, "Laptop 200", "200000", "ACTIVE", Instant.parse("2026-01-02T00:00:00Z"));
        product(shopA, categoryB, "Laptop 220", "220000", "ACTIVE", Instant.parse("2026-01-03T00:00:00Z"));
        product(shopB, categoryA, "Laptop 230", "230000", "ACTIVE", Instant.parse("2026-01-04T00:00:00Z"));
        product(shopA, categoryA, "Phone 200", "200000", "ACTIVE", Instant.parse("2026-01-05T00:00:00Z"));
        product(shopA, categoryA, "Laptop 300", "300000", "ACTIVE", Instant.parse("2026-01-06T00:00:00Z"));

        var result = catalog.search(criteria("laptop", "shop-a", "category-a",
                "150000", "250000", "priceAsc", 0, 12));

        assertThat(result.products()).extracting("name").containsExactly("Laptop 200");
        assertThat(result.totalItems()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(catalog.search(criteria(null, "unknown-shop", null,
                null, null, "newest", 0, 12)).products()).isEmpty();
    }

    @Test
    void paginationAndPriceAndNewestSortsAreDeterministic() {
        long category = category("sort", true);
        long shop = shop("sort-shop", "APPROVED");
        product(shop, category, "Old 300", "300000", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"));
        product(shop, category, "Mid 100", "100000", "ACTIVE", Instant.parse("2026-01-02T00:00:00Z"));
        product(shop, category, "New 200 A", "200000", "ACTIVE", Instant.parse("2026-01-03T00:00:00Z"));
        product(shop, category, "New 200 B", "200000", "ACTIVE", Instant.parse("2026-01-03T00:00:00Z"));
        product(shop, category, "Newest 400", "400000", "ACTIVE", Instant.parse("2026-01-04T00:00:00Z"));

        var page0 = catalog.search(criteria(null, null, null, null, null, "newest", 0, 2));
        var page1 = catalog.search(criteria(null, null, null, null, null, "newest", 1, 2));
        assertThat(page0.products()).extracting("name").containsExactly("Newest 400", "New 200 B");
        assertThat(page1.products()).extracting("name").containsExactly("New 200 A", "Mid 100");
        assertThat(page0.products()).extracting("id").doesNotContainAnyElementsOf(
                page1.products().stream().map(product -> product.id()).toList());
        assertThat(page0.totalItems()).isEqualTo(5);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.hasPrevious()).isFalse();
        assertThat(page0.hasNext()).isTrue();
        assertThat(page1.hasPrevious()).isTrue();

        assertThat(search(criteria(null, null, null, null, null, "priceAsc", 0, 12)))
                .containsExactly("Mid 100", "New 200 A", "New 200 B", "Old 300", "Newest 400");
        assertThat(search(criteria(null, null, null, null, null, "priceDesc", 0, 12)))
                .containsExactly("Newest 400", "Old 300", "New 200 B", "New 200 A", "Mid 100");
    }

    @Test
    void bestSellingCountsOnlyDeliveredOrdersAndRetainsZeroSaleProducts() {
        long category = category("sales", true);
        long shop = shop("sales-shop", "APPROVED");
        long buyer = user();
        long productA = product(shop, category, "Product A", "100000", "ACTIVE",
                Instant.parse("2026-01-04T00:00:00Z"));
        long productB = product(shop, category, "Product B", "100000", "ACTIVE",
                Instant.parse("2026-01-03T00:00:00Z"));
        long productC = product(shop, category, "Product C", "100000", "ACTIVE",
                Instant.parse("2026-01-02T00:00:00Z"));
        product(shop, category, "Product D", "100000", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"));

        orderItem(buyer, shop, productA, "DELIVERED", 2);
        orderItem(buyer, shop, productB, "DELIVERED", 5);
        orderItem(buyer, shop, productC, "CANCELLED", 10);
        orderItem(buyer, shop, productC, "RETURNED", 20);
        orderItem(buyer, shop, productC, "REFUNDED", 30);

        assertThat(search(criteria(null, null, null, null, null, "bestSelling", 0, 12)))
                .containsExactly("Product B", "Product A", "Product C", "Product D");
    }

    @Test
    void visibilityCannotBeBypassedBySearchOrBestSellingRanking() {
        long activeCategory = category("active", true);
        long disabledCategory = category("disabled", false);
        long approvedShop = shop("approved", "APPROVED");
        long suspendedShop = shop("suspended", "SUSPENDED");
        long buyer = user();
        long visible = product(approvedShop, activeCategory, "Target Visible", "100000", "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"));
        long hidden = product(approvedShop, activeCategory, "Target Hidden", "100000", "HIDDEN",
                Instant.parse("2026-01-02T00:00:00Z"));
        long moderated = product(approvedShop, activeCategory, "Target Moderated", "100000", "MODERATED",
                Instant.parse("2026-01-03T00:00:00Z"));
        long suspended = product(suspendedShop, activeCategory, "Target Suspended", "100000", "ACTIVE",
                Instant.parse("2026-01-04T00:00:00Z"));
        long disabled = product(approvedShop, disabledCategory, "Target Disabled", "100000", "ACTIVE",
                Instant.parse("2026-01-05T00:00:00Z"));
        orderItem(buyer, approvedShop, hidden, "DELIVERED", 100);
        orderItem(buyer, approvedShop, moderated, "DELIVERED", 100);
        orderItem(buyer, suspendedShop, suspended, "DELIVERED", 100);
        orderItem(buyer, approvedShop, disabled, "DELIVERED", 100);

        var result = catalog.search(criteria("target", null, null, null, null, "bestSelling", 0, 12));
        assertThat(result.products()).extracting("id").containsExactly(visible);
        assertThat(result.totalItems()).isEqualTo(1);
    }

    @Test
    void arbitrarySortNeverEntersSqlAndFallsBackToNewest() {
        long category = category("safe-sort", true);
        long shop = shop("safe-sort-shop", "APPROVED");
        product(shop, category, "Old", "100000", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"));
        product(shop, category, "New", "100000", "ACTIVE", Instant.parse("2026-01-02T00:00:00Z"));

        ProductSearchCriteria criteria = criteria(null, null, null, null, null,
                "price desc; drop table products", 0, 12);
        assertThat(search(criteria)).containsExactly("New", "Old");
        assertThat(catalog.search(criteria).criteria().getSort()).isEqualTo("newest");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.products", Integer.class)).isEqualTo(2);
    }

    private List<String> search(ProductSearchCriteria criteria) {
        return catalog.search(criteria).products().stream().map(product -> product.name()).toList();
    }

    private ProductSearchCriteria criteria(String q, String shop, String category,
            String minPrice, String maxPrice, String sort, int page, int size) {
        ProductSearchCriteria criteria = new ProductSearchCriteria();
        criteria.setQ(q);
        criteria.setShop(shop);
        criteria.setCategory(category);
        criteria.setMinPrice(minPrice == null ? null : new BigDecimal(minPrice));
        criteria.setMaxPrice(maxPrice == null ? null : new BigDecimal(maxPrice));
        criteria.setSort(sort);
        criteria.setPage(page);
        criteria.setSize(size);
        return criteria;
    }

    private long category(String slug, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.categories(name,slug,active)
                VALUES (?,?,?) RETURNING id
                """, Long.class, "Category " + slug, slug, active);
    }

    private long user() {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.users
                    (email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, key + "@example.test", key + "@example.test", "u" + key, "u" + key,
                "not-a-login-password");
    }

    private long shop(String slug, String status) {
        long owner = user();
        String rejection = "REJECTED".equals(status) ? "Rejected fixture" : null;
        String moderation = "SUSPENDED".equals(status) ? "Suspended fixture" : null;
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.shops
                    (owner_id,name,slug,description,pickup_address,status,rejection_reason,moderation_reason)
                VALUES (?, ?, ?, ?, 'Fixture pickup', ?, ?, ?) RETURNING id
                """, Long.class, owner, "Shop " + slug, slug, "Description " + slug,
                status, rejection, moderation);
    }

    private long product(long shop, long category, String name, String price, String status, Instant createdAt) {
        String moderation = "MODERATED".equals(status) ? "Moderated fixture" : null;
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.products
                    (shop_id,category_id,name,description,price,stock,status,moderation_reason,created_at,updated_at)
                VALUES (?,?,?, ?,?,10,?,?,?,?) RETURNING id
                """, Long.class, shop, category, name, "Description for " + name,
                new BigDecimal(price), status, moderation, Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void orderItem(long buyer, long shop, long product, String status, int quantity) {
        String key = UUID.randomUUID().toString();
        BigDecimal total = BigDecimal.valueOf(100L * quantity);
        Long order = jdbc.queryForObject("""
                INSERT INTO uteexpress.orders
                    (order_code,checkout_key,request_hash,buyer_id,shop_id,status,
                     receiver_name,phone,province_code,district,detail,subtotal,discount_total,
                     shipping_fee,grand_total,commission_amount,commission_rate_snapshot,delivered_at)
                VALUES (?,?,?,?,?,?, 'Receiver','0900000000','79','District','Detail',?,0,0,?,0,0,
                        CASE WHEN ?='DELIVERED' THEN CURRENT_TIMESTAMP ELSE NULL END)
                RETURNING id
                """, Long.class, "ORDER-" + key, "CHECKOUT-" + key, "HASH-" + key,
                buyer, shop, status, total, total, status);
        jdbc.update("""
                INSERT INTO uteexpress.order_items
                    (order_id,product_id,product_name_snapshot,unit_price,discount_snapshot,
                     final_unit_price,quantity,line_total)
                VALUES (?,?,?,100,0,100,?,?)
                """, order, product, "Product snapshot", quantity, total);
    }
}
