package com.uteexpress.catalog;

import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.service.CatalogQueryService;
import com.uteexpress.catalog.service.ProductDemoSeeder;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class ProductCatalogIT {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired CatalogQueryService catalog;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM uteexpress.product_images");
        jdbc.update("DELETE FROM uteexpress.products");
        jdbc.update("DELETE FROM uteexpress.shops");
        jdbc.update("DELETE FROM uteexpress.audit_logs");
        jdbc.update("DELETE FROM uteexpress.categories");
        jdbc.update("DELETE FROM uteexpress.otp_tokens");
        jdbc.update("DELETE FROM uteexpress.user_roles");
        jdbc.update("DELETE FROM uteexpress.users");
    }

    @Test
    void migrationCreatesValidatedTablesConstraintsIndexesAndHasNoPendingWork() {
        assertThat(tableExists("products")).isTrue();
        assertThat(tableExists("product_images")).isTrue();
        assertThat(constraints("products")).contains(
                "pk_products", "fk_products_shop_id", "fk_products_category_id",
                "ck_products_parent_ids", "ck_products_name_not_blank", "ck_products_price",
                "ck_products_stock_nonnegative", "ck_products_status",
                "ck_products_version_nonnegative");
        assertThat(constraints("product_images")).contains(
                "pk_product_images", "fk_product_images_product_id",
                "uq_product_images_product_id_position",
                "ck_product_images_product_id_positive",
                "ck_product_images_storage_key_not_blank",
                "ck_product_images_position_nonnegative");
        assertThat(indexes("products")).contains(
                "ix_products_category_id_status", "ix_products_shop_id_status");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void databaseEnforcesProductMoneyStockStatusAndParentConstraints() {
        Fixture fixture = approvedFixture("constraints");
        long valid = insertProduct(fixture.shopId(), fixture.categoryId(), "Valid", "125000.00", 2, "ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.products WHERE id = ?", Long.class, valid))
                .isZero();

        for (String price : List.of("0", "-1", "1.50")) {
            assertThatThrownBy(() -> insertProduct(fixture.shopId(), fixture.categoryId(),
                    "Invalid price " + price, price, 1, "ACTIVE"))
                    .isInstanceOf(DataAccessException.class);
        }
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status)
                VALUES (?,?,'NaN price','NaN'::numeric,1,'ACTIVE')
                """, fixture.shopId(), fixture.categoryId())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertProduct(fixture.shopId(), fixture.categoryId(),
                "Negative stock", "100", -1, "ACTIVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertProduct(fixture.shopId(), fixture.categoryId(),
                "Bad status", "100", 1, "DRAFT")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertProduct(Long.MAX_VALUE, fixture.categoryId(),
                "Bad shop", "100", 1, "ACTIVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertProduct(fixture.shopId(), Long.MAX_VALUE,
                "Bad category", "100", 1, "ACTIVE")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status,version)
                VALUES (?,?, 'Bad version',100,1,'ACTIVE',-1)
                """, fixture.shopId(), fixture.categoryId())).isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseEnforcesProductImageForeignKeyOrderingAndStorageKey() {
        Fixture fixture = approvedFixture("images");
        long productId = insertProduct(fixture.shopId(), fixture.categoryId(), "Image product", "100", 1, "ACTIVE");
        insertImage(productId, "demo/catalog/image.webp", 0);

        assertThatThrownBy(() -> insertImage(Long.MAX_VALUE, "missing/product.webp", 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertImage(productId, "duplicate/position.webp", 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertImage(productId, "negative/position.webp", -1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertImage(productId, "   ", 1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM uteexpress.products WHERE id = ?", productId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void catalogReturnsTrustedDatabaseSnapshotsInAscendingOrderAndIgnoresStockAvailability() {
        Fixture fixture = approvedFixture("query");
        long first = insertProduct(fixture.shopId(), fixture.categoryId(), "First", "125000", 0, "ACTIVE");
        long second = insertProduct(fixture.shopId(), fixture.categoryId(), "Second", "99000", 7, "ACTIVE");
        jdbc.update("UPDATE uteexpress.products SET version = 4 WHERE id = ?", second);

        List<ProductSnapshot> result = catalog.requirePurchasableProducts(
                new LinkedHashSet<>(List.of(second, first)));

        assertThat(result).extracting(ProductSnapshot::productId).containsExactly(first, second);
        assertThat(result.getFirst().shopId()).isEqualTo(fixture.shopId());
        assertThat(result.getFirst().productName()).isEqualTo("First");
        assertThat(result.getFirst().unitPrice()).isEqualByComparingTo("125000.00");
        assertThat(result.get(1).version()).isEqualTo(4L);
    }

    @Test
    void catalogRejectsEveryUnavailableCauseAndNeverReturnsPartialBatch() {
        Fixture validFixture = approvedFixture("valid");
        long valid = insertProduct(validFixture.shopId(), validFixture.categoryId(),
                "Valid", "100", 1, "ACTIVE");
        long hidden = insertProduct(validFixture.shopId(), validFixture.categoryId(),
                "Hidden", "100", 1, "HIDDEN");

        long pendingShop = createShop("pending", "PENDING");
        long pendingProduct = insertProduct(pendingShop, validFixture.categoryId(),
                "Pending Shop", "100", 1, "ACTIVE");
        long rejectedShop = createShop("rejected", "REJECTED");
        long rejectedProduct = insertProduct(rejectedShop, validFixture.categoryId(),
                "Rejected Shop", "100", 1, "ACTIVE");
        long inactiveCategory = createCategory("inactive", false);
        long inactiveProduct = insertProduct(validFixture.shopId(), inactiveCategory,
                "Inactive Category", "100", 1, "ACTIVE");

        for (long unavailable : List.of(hidden, pendingProduct, rejectedProduct, inactiveProduct, Long.MAX_VALUE)) {
            assertThatThrownBy(() -> catalog.requirePurchasableProducts(Set.of(valid, unavailable)))
                    .isInstanceOfSatisfying(ApplicationException.class,
                            error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
        assertThat(catalog.requirePurchasableProducts(Set.of())).isEmpty();
    }

    @Test
    void explicitDemoSeederRequiresApprovedPrerequisitesIsIdempotentAndPreservesEdits() {
        Fixture fixture = approvedFixture("demo");
        ProductDemoSeeder seeder = new ProductDemoSeeder(jdbc, fixture.shopSlug(), fixture.categorySlug());

        seeder.run(null);
        long activeId = jdbc.queryForObject(
                "SELECT id FROM uteexpress.products WHERE shop_id = ? AND name = ?",
                Long.class, fixture.shopId(), "Bưu kiện mẫu tiêu chuẩn");
        jdbc.update("UPDATE uteexpress.products SET price = 777000, stock = 3, version = 5 WHERE id = ?", activeId);
        seeder.run(null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.products WHERE shop_id = ?",
                Integer.class, fixture.shopId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT price FROM uteexpress.products WHERE id = ?",
                BigDecimal.class, activeId)).isEqualByComparingTo("777000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.products WHERE status = 'ACTIVE' AND stock = 0",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.products WHERE status = 'HIDDEN' AND stock > 0",
                Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> new ProductDemoSeeder(jdbc, "missing-shop", fixture.categorySlug()).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved Shop");
    }

    private boolean tableExists(String table) {
        return jdbc.queryForObject("""
                SELECT count(*) > 0 FROM information_schema.tables
                 WHERE table_schema = 'uteexpress' AND table_name = ?
                """, Boolean.class, table);
    }

    private List<String> constraints(String table) {
        return jdbc.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                 WHERE table_schema = 'uteexpress' AND table_name = ?
                """, String.class, table);
    }

    private List<String> indexes(String table) {
        return jdbc.queryForList("""
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = 'uteexpress' AND tablename = ?
                """, String.class, table);
    }

    private Fixture approvedFixture(String key) {
        String suffix = key + "-" + UUID.randomUUID().toString().substring(0, 8);
        String shopSlug = "shop-" + suffix;
        String categorySlug = "category-" + suffix;
        return new Fixture(createShop(shopSlug, "APPROVED"), createCategory(categorySlug, true),
                shopSlug, categorySlug);
    }

    private long createShop(String slugPrefix, String status) {
        String slug = slugPrefix.contains("-") ? slugPrefix : slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Long ownerId = jdbc.queryForObject("""
                INSERT INTO uteexpress.users
                    (email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, username + "@example.test", username + "@example.test",
                username, username, "not-a-login-password");
        String rejection = "REJECTED".equals(status) ? "Rejected test fixture" : null;
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.shops
                    (owner_id,name,slug,pickup_address,status,rejection_reason)
                VALUES (?, 'Fixture Shop', ?, 'Fixture pickup', ?, ?) RETURNING id
                """, Long.class, ownerId, slug, status, rejection);
    }

    private long createCategory(String slugPrefix, boolean active) {
        String slug = slugPrefix.contains("-") ? slugPrefix : slugPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.categories(name,slug,active)
                VALUES ('Fixture Category', ?, ?) RETURNING id
                """, Long.class, slug, active);
    }

    private long insertProduct(long shopId, long categoryId, String name, String price, int stock, String status) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status)
                VALUES (?,?,?,?,?,?) RETURNING id
                """, Long.class, shopId, categoryId, name, new BigDecimal(price), stock, status);
    }

    private void insertImage(long productId, String storageKey, int position) {
        jdbc.update("""
                INSERT INTO uteexpress.product_images(product_id,storage_key,position,alt_text)
                VALUES (?,?,?,'Synthetic test key only')
                """, productId, storageKey, position);
    }

    private record Fixture(Long shopId, Long categoryId, String shopSlug, String categorySlug) {
    }
}
