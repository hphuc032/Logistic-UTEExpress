package com.uteexpress.catalog;

import com.uteexpress.catalog.service.ProductDemoSeeder;
import com.uteexpress.catalog.service.PublicCatalogService;
import com.uteexpress.common.storage.FileStorageService;
import com.uteexpress.common.storage.ImageStoragePolicy;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.support.TestImages;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class PublicCatalogIT {
    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("uteexpress.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired PublicCatalogService catalog;
    @Autowired FileStorageService storage;
    @Autowired MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE uteexpress.users, uteexpress.categories, uteexpress.shops, "
                + "uteexpress.products RESTART IDENTITY CASCADE");
    }

    @Test
    void schemaIsValidatedAndProd01NeedsNoMigration() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(entityManagerFactory.isOpen()).isTrue();
    }

    @Test
    void homeAndBasicListUseOneVisibilityRuleAcrossMultipleShops() throws Exception {
        long activeCategory = category("public", true);
        long disabledCategory = category("disabled", false);
        long firstShop = shop("approved-one", "APPROVED");
        long secondShop = shop("approved-two", "APPROVED");
        long suspendedShop = shop("suspended", "SUSPENDED");

        for (int index = 1; index <= 6; index++) {
            product(firstShop, activeCategory, "Public A" + index, "ACTIVE", index == 1 ? 0 : 5);
            product(secondShop, activeCategory, "Public B" + index, "ACTIVE", 5);
        }
        product(firstShop, activeCategory, "Hidden catalog item", "HIDDEN", 5);
        product(firstShop, activeCategory, "Moderated catalog item", "MODERATED", 5);
        product(suspendedShop, activeCategory, "Suspended shop item", "ACTIVE", 5);
        product(firstShop, disabledCategory, "Disabled category item", "ACTIVE", 5);

        var home = catalog.home();
        assertThat(home.products()).hasSize(12);
        assertThat(home.products()).extracting("shopSlug").contains("approved-one", "approved-two");
        assertThat(home.products()).anySatisfy(item -> assertThat(item.stock()).isZero());
        assertThat(catalog.products()).hasSize(12);

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Public A1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Public B1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Hidden catalog item"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Moderated catalog item"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Suspended shop item"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Disabled category item"))));
        mvc.perform(get("/products")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hết hàng")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Moderated catalog item"))));
    }

    @Test
    void detailsShopAndCategoryFailClosedForEveryNonPublicState() throws Exception {
        long activeCategory = category("active-category", true);
        long disabledCategory = category("disabled-category", false);
        long approvedShop = shop("approved-shop", "APPROVED");
        long suspendedShop = shop("suspended-shop", "SUSPENDED");
        long pendingShop = shop("pending-shop", "PENDING");
        long rejectedShop = shop("rejected-shop", "REJECTED");
        long visible = product(approvedShop, activeCategory, "Visible product", "ACTIVE", 3);
        long hidden = product(approvedShop, activeCategory, "Hidden product", "HIDDEN", 3);
        long moderated = product(approvedShop, activeCategory, "Moderated product", "MODERATED", 3);
        long suspended = product(suspendedShop, activeCategory, "Suspended product", "ACTIVE", 3);
        long disabled = product(approvedShop, disabledCategory, "Disabled product", "ACTIVE", 3);

        mvc.perform(get("/products/{id}", visible)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Visible product")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("approved-shop")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("active-category")));
        for (long id : new long[]{hidden, moderated, suspended, disabled, Long.MAX_VALUE}) {
            mvc.perform(get("/products/{id}", id)).andExpect(status().isNotFound());
        }

        mvc.perform(get("/shops/approved-shop")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Visible product")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Hidden product"))));
        for (String slug : new String[]{"suspended-shop", "pending-shop", "rejected-shop", "missing-shop"}) {
            mvc.perform(get("/shops/{slug}", slug)).andExpect(status().isNotFound());
        }

        mvc.perform(get("/categories/active-category")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Visible product")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Suspended product"))));
        mvc.perform(get("/categories/disabled-category")).andExpect(status().isNotFound());
    }

    @Test
    void publicMediaRequiresProductVisibilityAndImageOwnership() throws Exception {
        long activeCategory = category("media", true);
        long approvedShop = shop("media-shop", "APPROVED");
        long suspendedShop = shop("media-suspended", "SUSPENDED");
        long visible = product(approvedShop, activeCategory, "Visible media", "ACTIVE", 2);
        long other = product(approvedShop, activeCategory, "Other media", "ACTIVE", 2);
        long hidden = product(approvedShop, activeCategory, "Hidden media", "HIDDEN", 2);
        long moderated = product(approvedShop, activeCategory, "Moderated media", "MODERATED", 2);
        long suspended = product(suspendedShop, activeCategory, "Suspended media", "ACTIVE", 2);
        var stored = storage.storeImage("products", new UploadContent(TestImages.png(), "public.png", "image/png"),
                new ImageStoragePolicy(5L * 1024 * 1024, 4096, 4096));
        long visibleImage = image(visible, stored.key(), "Public image");
        long otherImage = image(other, stored.key(), "Other image");
        long hiddenImage = image(hidden, stored.key(), "Hidden image");
        long moderatedImage = image(moderated, stored.key(), "Moderated image");
        long suspendedImage = image(suspended, stored.key(), "Suspended image");

        mvc.perform(get("/products/{productId}/images/{imageId}/content", visible, visibleImage))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/products/{productId}/images/{imageId}/content", visible, otherImage))
                .andExpect(status().isNotFound());
        for (long[] pair : new long[][]{{hidden, hiddenImage}, {moderated, moderatedImage},
                {suspended, suspendedImage}}) {
            mvc.perform(get("/products/{productId}/images/{imageId}/content", pair[0], pair[1]))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void vendorControlledCatalogTextIsHtmlEscaped() throws Exception {
        String script = "<script>alert(1)</script>";
        long category = category("safe-category", true, script);
        long shop = shop("safe-shop", "APPROVED", script);
        long product = product(shop, category, script, script, "ACTIVE", 1);
        var stored = storage.storeImage("products",
                new UploadContent(TestImages.png(), "xss.png", "image/png"),
                new ImageStoragePolicy(5L * 1024 * 1024, 4096, 4096));
        image(product, stored.key(), script);

        mvc.perform(get("/products/{id}", product))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(script))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "&lt;script&gt;alert(1)&lt;/script&gt;")));
    }

    @Test
    void moderationAndShopSuspensionTakeEffectImmediatelyAndRestoreSafely() throws Exception {
        long category = category("transitions", true);
        long shop = shop("transition-shop", "APPROVED");
        long product = product(shop, category, "Transition product", "ACTIVE", 3);

        mvc.perform(get("/products/{id}", product)).andExpect(status().isOk());
        jdbc.update("UPDATE uteexpress.products SET status='MODERATED', moderation_reason='Policy' WHERE id=?", product);
        mvc.perform(get("/products/{id}", product)).andExpect(status().isNotFound());
        jdbc.update("UPDATE uteexpress.products SET status='ACTIVE' WHERE id=?", product);
        mvc.perform(get("/products/{id}", product)).andExpect(status().isOk());

        jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED', moderation_reason='Policy' WHERE id=?", shop);
        mvc.perform(get("/products/{id}", product)).andExpect(status().isNotFound());
        mvc.perform(get("/shops/transition-shop")).andExpect(status().isNotFound());
        jdbc.update("UPDATE uteexpress.shops SET status='APPROVED' WHERE id=?", shop);
        mvc.perform(get("/products/{id}", product)).andExpect(status().isOk());
        mvc.perform(get("/shops/transition-shop")).andExpect(status().isOk());
    }

    @Test
    void demoSeedCreatesTwelveVisibleProductsAcrossTwoShopsAndIsIdempotent() throws Exception {
        long category = category("demo-category", true);
        long primaryShop = shop("demo-shop", "APPROVED");
        ProductDemoSeeder seeder = new ProductDemoSeeder(jdbc, "demo-shop", "demo-category");

        seeder.run(null);
        Long edited = jdbc.queryForObject("SELECT id FROM uteexpress.products WHERE shop_id=? AND name=?",
                Long.class, primaryShop, "Bưu kiện mẫu tiêu chuẩn");
        jdbc.update("UPDATE uteexpress.products SET price=777000, version=5 WHERE id=?", edited);
        seeder.run(null);

        assertThat(catalog.home().products()).hasSize(12);
        assertThat(catalog.home().products().stream().map(item -> item.shopSlug()).distinct()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.products", Integer.class)).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT price FROM uteexpress.products WHERE id=?", BigDecimal.class, edited))
                .isEqualByComparingTo("777000");
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bưu kiện mẫu tiêu chuẩn")));
        assertThat(category).isPositive();
    }

    private long category(String slug, boolean active) {
        return category(slug, active, "Category " + slug);
    }

    private long category(String slug, boolean active, String name) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.categories(name,slug,active)
                VALUES (?,?,?) RETURNING id
                """, Long.class, name, slug, active);
    }

    private long shop(String slug, String status) {
        return shop(slug, status, "Shop " + slug);
    }

    private long shop(String slug, String status, String name) {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Long owner = jdbc.queryForObject("""
                INSERT INTO uteexpress.users
                    (email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, key + "@example.test", key + "@example.test", "u" + key, "u" + key,
                "not-a-login-password");
        String rejection = "REJECTED".equals(status) ? "Rejected fixture" : null;
        String moderation = "SUSPENDED".equals(status) ? "Suspended fixture" : null;
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.shops
                    (owner_id,name,slug,description,pickup_address,status,rejection_reason,moderation_reason)
                VALUES (?, ?, ?, ?, 'Fixture pickup', ?, ?, ?) RETURNING id
                """, Long.class, owner, name, slug, "Description " + slug, status, rejection, moderation);
    }

    private long product(long shop, long category, String name, String status, int stock) {
        return product(shop, category, name, "Description for " + name, status, stock);
    }

    private long product(long shop, long category, String name, String description, String status, int stock) {
        String moderation = "MODERATED".equals(status) ? "Moderated fixture" : null;
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.products
                    (shop_id,category_id,name,description,price,stock,status,moderation_reason)
                VALUES (?,?,?, ?,125000,?,?,?) RETURNING id
                """, Long.class, shop, category, name, description, stock, status, moderation);
    }

    private long image(long product, String key, String alt) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.product_images(product_id,storage_key,position,alt_text)
                VALUES (?,?,0,?) RETURNING id
                """, Long.class, product, key, alt);
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("uteexpress-public-catalog-");
        } catch (java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
