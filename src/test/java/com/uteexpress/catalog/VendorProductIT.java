package com.uteexpress.catalog;

import com.uteexpress.catalog.dto.ProductCreateRequest;
import com.uteexpress.catalog.dto.ProductUpdateRequest;
import com.uteexpress.catalog.dto.StockQuantity;
import com.uteexpress.catalog.dto.ProductView;
import com.uteexpress.catalog.service.CatalogQueryService;
import com.uteexpress.catalog.service.InventoryService;
import com.uteexpress.catalog.service.ProductService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.storage.UploadContent;
import com.uteexpress.security.RoleCode;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.support.TestImages;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import jakarta.validation.ConstraintViolationException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class VendorProductIT {
    private static final String PASSWORD = "VendorSecret1";
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
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ProductService productService;
    @Autowired InventoryService inventory;
    @Autowired CatalogQueryService catalog;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE TABLE uteexpress.users, uteexpress.categories, uteexpress.shops, "
                + "uteexpress.products RESTART IDENTITY CASCADE");
    }

    @Test
    void existingSchemaSupportsVendor02AndFlywayHasNoPendingMigration() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.tables
                 where table_schema='uteexpress' and table_name in ('products','product_images')
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                 where table_schema='uteexpress' and table_name='products'
                """, String.class)).contains("ck_products_price", "ck_products_stock_nonnegative");
    }

    @Test
    void approvedVendorCrudValidationOptimisticConflictAndSoftDeletePreserveOrderHistory() throws Exception {
        Fixture fixture = fixture("crud", "VENDOR");
        ProductView created = as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Original", "100000", 5)));
        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.version()).isZero();

        ProductUpdateRequest valid = update(fixture.categoryId(), "Updated", "120000", 7, created.version());
        ProductView updated = as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.update(created.id(), valid));
        assertThat(updated.name()).isEqualTo("Updated");
        assertThat(updated.version()).isEqualTo(1L);

        jdbc.update("update uteexpress.products set stock=stock-1, version=version+1 where id=?", created.id());
        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.update(created.id(), update(fixture.categoryId(), "Stale", "130000", 9, 1L))))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));

        long orderId = insertOrder(fixture.userId(), fixture.shopId());
        jdbc.update("""
                insert into uteexpress.order_items
                    (order_id,product_id,product_name_snapshot,unit_price,discount_snapshot,
                     final_unit_price,quantity,line_total)
                values (?,?,?,120000,0,120000,1,120000)
                """, orderId, created.id(), "Updated snapshot");

        Long currentVersion = jdbc.queryForObject("select version from uteexpress.products where id=?",
                Long.class, created.id());
        as(fixture.userId(), RoleCode.VENDOR, () -> {
            productService.hide(created.id(), currentVersion);
            return null;
        });

        assertThat(jdbc.queryForObject("select status from uteexpress.products where id=?",
                String.class, created.id())).isEqualTo("HIDDEN");
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.order_items where order_id=?",
                Integer.class, orderId)).isOne();
        assertThat(jdbc.queryForMap("select product_id,product_name_snapshot,unit_price from uteexpress.order_items where order_id=?",
                orderId)).containsEntry("product_id", created.id())
                .containsEntry("product_name_snapshot", "Updated snapshot");
        assertThatThrownBy(() -> catalog.requirePurchasableProducts(Set.of(created.id())))
                .isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Bad price", "100.50", 1))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Zero price", "0", 1))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Negative stock", "100", -1))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), " ", "100", 1))))
                .isInstanceOf(ConstraintViolationException.class);
        long disabled = category("disabled", false);
        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(disabled, "Disabled", "100", 1))))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(Long.MAX_VALUE, "Missing", "100", 1))))
                .isInstanceOf(ApplicationException.class);
    }

    @Test
    void t14CrossShopCrudAndMediaAreDeniedAndImageContentIsOwned() throws Exception {
        Fixture vendorA = fixture("vendor-a", "VENDOR");
        Fixture vendorB = fixture("vendor-b", "VENDOR");
        ProductView productB = as(vendorB.userId(), RoleCode.VENDOR,
                () -> productService.create(request(vendorB.categoryId(), "Vendor B", "100", 2)));
        var image = as(vendorB.userId(), RoleCode.VENDOR,
                () -> productService.addImage(productB.id(),
                        new UploadContent(TestImages.png(), "product.png", "image/png"), "Vendor B image"));

        assertNotFound(() -> as(vendorA.userId(), RoleCode.VENDOR,
                () -> productService.getCurrentShopProduct(productB.id())));
        assertNotFound(() -> as(vendorA.userId(), RoleCode.VENDOR,
                () -> productService.update(productB.id(), update(vendorA.categoryId(), "Attack", "100", 1, 0L))));
        assertNotFound(() -> as(vendorA.userId(), RoleCode.VENDOR, () -> {
            productService.hide(productB.id(), 0L); return null;
        }));
        assertNotFound(() -> as(vendorA.userId(), RoleCode.VENDOR,
                () -> productService.addImage(productB.id(),
                        new UploadContent(TestImages.png(), "attack.png", "image/png"), null)));
        assertNotFound(() -> as(vendorA.userId(), RoleCode.VENDOR, () -> {
            productService.deleteImage(productB.id(), image.id()); return null;
        }));
        ProductView secondB = as(vendorB.userId(), RoleCode.VENDOR,
                () -> productService.create(request(vendorB.categoryId(), "Second B", "100", 1)));
        assertNotFound(() -> as(vendorB.userId(), RoleCode.VENDOR, () -> {
            productService.deleteImage(secondB.id(), image.id()); return null;
        }));
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.product_images where id=?",
                Integer.class, image.id())).isOne();

        Cookie vendorJwt = login(vendorB.username());
        mvc.perform(get("/vendor/products/{id}/images/{imageId}/content", productB.id(), image.id())
                        .cookie(vendorJwt))
                .andExpect(status().isOk()).andExpect(content().bytes(TestImages.png()))
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void sec02MalformedImageWithForgedOwnerFieldsLeavesDatabaseAndStorageUnchanged() throws Exception {
        Fixture fixture = fixture("sec02-upload", "VENDOR");
        ProductView product = as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Image target", "100", 2)));
        Cookie jwt = login(fixture.username());
        Set<Path> before = storedFiles();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/vendor/products/{id}/images", product.id())
                        .file(new org.springframework.mock.web.MockMultipartFile("image", "../../evil.png",
                                "image/png", "<script>alert(1)</script>".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .cookie(jwt).with(csrf()).param("shopId", "999999").param("ownerId", "999999")
                        .param("storageKey", "../../escape.png").param("moderationStatus", "ACTIVE"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.product_images where product_id=?",
                Integer.class, product.id())).isZero();
        assertThat(storedFiles()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select shop_id from uteexpress.products where id=?", Long.class, product.id()))
                .isEqualTo(fixture.shopId());
    }

    @Test
    void sec02RealTransactionRollbackCleansNewImageAndRetainsDeletedImage() throws Exception {
        Fixture fixture = fixture("sec02-rollback", "VENDOR");
        ProductView product = as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Rollback target", "100", 2)));
        var image = as(fixture.userId(), RoleCode.VENDOR, () -> productService.addImage(product.id(),
                new UploadContent(TestImages.png(), "safe.png", "image/png"), null));
        Set<Path> before = storedFiles();
        TransactionTemplate tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR, () -> {
            tx.executeWithoutResult(status -> {
                productService.addImage(product.id(), new UploadContent(TestImages.png(), "new.png", "image/png"), null);
                throw new ForcedRollback();
            });
            return null;
        })).isInstanceOf(ForcedRollback.class);
        assertThat(storedFiles()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.product_images where product_id=?",
                Integer.class, product.id())).isOne();
        assertThatThrownBy(() -> as(fixture.userId(), RoleCode.VENDOR, () -> {
            tx.executeWithoutResult(status -> {
                productService.deleteImage(product.id(), image.id());
                throw new ForcedRollback();
            });
            return null;
        })).isInstanceOf(ForcedRollback.class);
        assertThat(storedFiles()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from uteexpress.product_images where id=?",
                Integer.class, image.id())).isOne();
        assertThat(as(fixture.userId(), RoleCode.VENDOR, () -> productService.readImage(product.id(), image.id())).bytes())
                .isEqualTo(TestImages.png());
    }

    private Set<Path> storedFiles() throws IOException {
        if (!Files.exists(STORAGE_ROOT)) return Set.of();
        try (var files = Files.walk(STORAGE_ROOT)) {
            return files.filter(Files::isRegularFile).collect(java.util.stream.Collectors.toSet());
        }
    }

    @Test
    void vendorRoleRealJwtAndCsrfContractsAreEnforced() throws Exception {
        Fixture vendor = fixture("jwt-vendor", "VENDOR");
        Fixture user = fixture("jwt-user", "USER");
        Cookie vendorJwt = login(vendor.username());
        Cookie userJwt = login(user.username());

        mvc.perform(get("/vendor/products").cookie(vendorJwt)).andExpect(status().isOk());
        mvc.perform(get("/vendor/products").cookie(userJwt)).andExpect(status().isForbidden());
        mvc.perform(post("/vendor/products").cookie(vendorJwt)
                        .param("name", "No CSRF").param("price", "100")
                        .param("stock", "1").param("categoryId", vendor.categoryId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void productManagementRequiresAnApprovedOwnedShop() {
        long categoryId = category("eligibility", true);
        long noShop = user("no_shop", "VENDOR");
        assertDenied(() -> as(noShop, RoleCode.VENDOR,
                () -> productService.create(request(categoryId, "No shop", "100", 1))));

        for (String state : List.of("PENDING", "REJECTED")) {
            String key = state.toLowerCase(Locale.ROOT);
            long owner = user(key + "_owner", "VENDOR");
            String rejectionReason = state.equals("REJECTED") ? "Fixture rejection" : null;
            jdbc.update("""
                    insert into uteexpress.shops(
                        owner_id, name, slug, pickup_address, status, rejection_reason
                    ) values (?, ?, ?, 'UTE', ?, ?)
                    """, owner, state + " Shop", key + "-shop", state, rejectionReason);
            assertDenied(() -> as(owner, RoleCode.VENDOR,
                    () -> productService.create(request(categoryId, state, "100", 1))));
        }
    }

    @Test
    void inventoryRequiresCallerTransactionRejectsPartialBatchAndRollsBackWithCaller() throws Exception {
        Fixture fixture = fixture("inventory", "VENDOR");
        ProductView available = as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Available", "100", 5)));
        ProductView empty = as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Empty", "100", 0)));

        assertThatThrownBy(() -> inventory.lockAndCheck(List.of(new StockQuantity(available.id(), 1))))
                .isInstanceOf(IllegalTransactionStateException.class);

        TransactionTemplate tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventory.decrease(
                List.of(new StockQuantity(available.id(), 1)))))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventory.lockAndCheck(List.of(
                new StockQuantity(available.id(), 1), new StockQuantity(empty.id(), 1)))))
                .isInstanceOf(ApplicationException.class);
        assertThat(stock(available.id())).isEqualTo(5);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            List<StockQuantity> batch = List.of(new StockQuantity(available.id(), 2));
            inventory.lockAndCheck(batch);
            inventory.decrease(batch);
            throw new ForcedRollback();
        })).isInstanceOf(ForcedRollback.class);
        assertThat(stock(available.id())).isEqualTo(5);

        tx.executeWithoutResult(status -> inventory.restore(List.of(new StockQuantity(available.id(), 2))));
        assertThat(stock(available.id())).isEqualTo(7);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            inventory.restore(List.of(new StockQuantity(available.id(), 2)));
            throw new ForcedRollback();
        })).isInstanceOf(ForcedRollback.class);
        assertThat(stock(available.id())).isEqualTo(7);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventory.restore(List.of(
                new StockQuantity(available.id(), 1), new StockQuantity(available.id(), 1)))))
                .isInstanceOf(ApplicationException.class);

        jdbc.update("update uteexpress.products set stock=? where id=?", Integer.MAX_VALUE, available.id());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventory.restore(
                List.of(new StockQuantity(available.id(), 1)))))
                .isInstanceOf(ApplicationException.class);
        assertThat(stock(available.id())).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void t23ConcurrentBuyersCompetingForLastItemHaveAtMostOneSuccess() throws Exception {
        Fixture fixture = fixture("last-item", "VENDOR");
        ProductView product = as(fixture.userId(), RoleCode.VENDOR,
                () -> productService.create(request(fixture.categoryId(), "Last item", "100", 1)));
        CountDownLatch start = new CountDownLatch(1);
        TransactionTemplate template = new TransactionTemplate(transactions);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> purchase = () -> {
                start.await();
                try {
                    template.executeWithoutResult(status -> {
                        List<StockQuantity> batch = List.of(new StockQuantity(product.id(), 1));
                        inventory.lockAndCheck(batch);
                        inventory.decrease(batch);
                    });
                    return true;
                } catch (ApplicationException rejected) {
                    return false;
                }
            };
            var first = executor.submit(purchase);
            var second = executor.submit(purchase);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(stock(product.id())).isZero();
    }

    private Fixture fixture(String key, String role) {
        String username = key.replace('-', '_');
        long userId = user(username, role);
        long shopId = jdbc.queryForObject("""
                insert into uteexpress.shops(owner_id,name,slug,pickup_address,status)
                values (?, ?, ?, 'UTE', 'APPROVED') returning id
                """, Long.class, userId, "Shop " + key, "shop-" + key);
        long categoryId = category("category-" + key, true);
        return new Fixture(userId, username, shopId, categoryId);
    }

    private long user(String username, String role) {
        Long id = jdbc.queryForObject("""
                insert into uteexpress.users
                    (email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                values (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) returning id
                """, Long.class, username + "@example.com", username.toLowerCase(Locale.ROOT) + "@example.com",
                username, username.toLowerCase(Locale.ROOT), passwordEncoder.encode(PASSWORD));
        jdbc.update("""
                insert into uteexpress.user_roles(user_id,role_id)
                select ?, id from uteexpress.roles where code=?
                """, id, role);
        return id;
    }

    private long category(String key, boolean active) {
        return jdbc.queryForObject("""
                insert into uteexpress.categories(name,slug,active)
                values (?, ?, ?) returning id
                """, Long.class, "Category " + key, key, active);
    }

    private long insertOrder(long buyerId, long shopId) {
        return jdbc.queryForObject("""
                insert into uteexpress.orders
                    (order_code,checkout_key,request_hash,buyer_id,shop_id,status,
                     receiver_name,phone,province_code,district,detail,
                     subtotal,discount_total,shipping_fee,grand_total,
                     commission_amount,commission_rate_snapshot)
                values (?,?,?, ?,?,'NEW','Receiver','0900','79','District','Detail',
                        120000,0,0,120000,0,0) returning id
                """, Long.class, "ORD-" + UUID.randomUUID(), "checkout-" + UUID.randomUUID(),
                "hash-" + UUID.randomUUID(), buyerId, shopId);
    }

    private Cookie login(String username) throws Exception {
        return mvc.perform(post("/login").with(csrf()).param("identifier", username).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
    }

    private <T> T as(long userId, RoleCode role, Callable<T> action) throws Exception {
        var principal = new UteExpressPrincipal(userId, "vendor", "hash", 0,
                List.of(new SimpleGrantedAuthority(role.authority())), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try { return action.call(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    private int stock(long productId) {
        return jdbc.queryForObject("select stock from uteexpress.products where id=?", Integer.class, productId);
    }

    private static ProductCreateRequest request(long categoryId, String name, String price, int stock) {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setName(name);
        request.setDescription("Description");
        request.setPrice(new BigDecimal(price));
        request.setStock(stock);
        request.setCategoryId(categoryId);
        return request;
    }

    private static ProductUpdateRequest update(long categoryId, String name, String price, int stock, long version) {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName(name);
        request.setDescription("Description");
        request.setPrice(new BigDecimal(price));
        request.setStock(stock);
        request.setCategoryId(categoryId);
        request.setVersion(version);
        return request;
    }

    private static Path temporaryStorageRoot() {
        try { return Files.createTempDirectory("uteexpress-vendor02-" + UUID.randomUUID()); }
        catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }

    private record Fixture(long userId, String username, long shopId, Long categoryId) { }
    private static final class ForcedRollback extends RuntimeException { }
}
