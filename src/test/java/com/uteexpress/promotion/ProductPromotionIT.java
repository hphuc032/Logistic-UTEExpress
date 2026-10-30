package com.uteexpress.promotion;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.catalog.dto.*;
import com.uteexpress.catalog.service.*;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import com.uteexpress.common.exception.*;
import com.uteexpress.engagement.service.EngagementService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.PromotionPricingService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class ProductPromotionIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ProductPromotionManagementService management;
    @Autowired CatalogQueryService catalog;
    @Autowired PublicCatalogService publicCatalog;
    @Autowired CheckoutQuoteService quotes;
    @Autowired OrderPlacementService placement;
    @Autowired VendorOrderService vendorOrders;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired EngagementService engagement;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean CartService carts;
    long vendor, otherVendor, buyer, shop, foreignShop, category, product, foreignProduct, address, provider;
    String username, key;
    private static final String PASSWORD = "PromoVendor1!";

    @BeforeEach void fixture() {
        reset((CartService) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(carts));
        for (String table : List.of("voucher_usages", "vouchers", "order_status_history", "payments", "order_items", "orders", "commission_policies", "promotions"))
            jdbc.update("DELETE FROM uteexpress." + table);
        key = UUID.randomUUID().toString();
        username = "p" + key.replace("-", "").substring(0, 20);
        vendor = account(username, "VENDOR"); otherVendor = account("o" + username, "VENDOR"); buyer = account("b" + username, "USER");
        shop = shop(vendor); foreignShop = shop(otherVendor);
        category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id", Long.class, key);
        product = product(shop, "125000"); foreignProduct = product(foreignShop, "125000");
        address = jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Receiver','0900000000','VN','District','Detail',true) RETURNING id", Long.class, buyer);
        provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id", Long.class, "P" + username.toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',5000,true)", provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7.1234,'2020-01-01',?)", vendor);
        authenticate(buyer, "USER"); carts.addProduct(new AddCartProductRequest(product, 2));
    }
    @AfterEach void clear() { reset((CartService) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(carts)); SecurityContextHolder.clearContext(); }

    @Test void nonSuperuserDatabaseOwnerRunsAllMigrationsAndInstallsExtension() throws Exception {
        String role = "promo_role", database = "promo_role_probe", password = UUID.randomUUID().toString();
        jdbc.execute("CREATE ROLE " + role + " LOGIN NOSUPERUSER PASSWORD '" + password + "'");
        jdbc.execute("CREATE DATABASE " + database + " OWNER " + role);
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
        Flyway migration = Flyway.configure().dataSource(url, role, password).defaultSchema("uteexpress").schemas("uteexpress").load();
        int pending = migration.info().pending().length;
        assertThat(pending).isGreaterThanOrEqualTo(20);
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(pending);
        assertThat(migration.info().pending()).isEmpty();
        migration.validate(); assertThat(migration.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, role, password); var statement = connection.createStatement()) {
            var rows = statement.executeQuery("SELECT rolsuper, extversion FROM pg_roles JOIN pg_extension ON extname='btree_gist' WHERE rolname=current_user");
            assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isFalse(); assertThat(rows.getString(2)).isEqualTo("1.7");
        }
    }
    @Test void vendorFormCreatesOwnedPromotionAndConvertsVietnamTime() throws Exception {
        mvc.perform(valid(post(base(product))).with(user(principal(vendor, "VENDOR"))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        var row = jdbc.queryForMap("SELECT * FROM uteexpress.promotions WHERE product_id=?", product);
        assertThat(row).containsEntry("product_id", product).containsEntry("created_by", vendor).containsEntry("version", 0L);
        assertThat(jdbc.queryForObject("SELECT starts_at AT TIME ZONE 'UTC' FROM uteexpress.promotions WHERE product_id=?", LocalDateTime.class, product))
                .isEqualTo(LocalDateTime.of(2020, 1, 1, 17, 0));
        mvc.perform(get(base(product)).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get(base(product) + "/new").with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("datetime-local")));
    }
    @Test void editDisableAndEnableAreVersionedAndNeverChangeOwnership() throws Exception {
        long id = create();
        mvc.perform(get(path(product, id, "/edit")).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"version\"")));
        mvc.perform(valid(post(path(product, id, "/update"))).param("version", "0").with(user(principal(vendor, "VENDOR"))).with(csrf()))
                .andExpect(status().is3xxRedirection()); // unchanged update may keep its version
        long version = version(id);
        mvc.perform(post(path(product, id, "/disable")).param("version", "" + version).with(user(principal(vendor, "VENDOR"))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.promotions WHERE id=?", id)).containsEntry("active", false)
                .containsEntry("product_id", product).containsEntry("created_by", vendor);
        var update = updateForm(version(id)); update.setDiscountPercent(new BigDecimal("25"));
        authenticate(vendor, "VENDOR"); management.update(product, id, update);
        assertThat(management.get(product, id).active()).isTrue();
        rejects(() -> management.update(product, id, update), ErrorCode.CONFLICT);
        rejects(() -> management.disable(product, id, version), ErrorCode.CONFLICT);
    }
    @ParameterizedTest @ValueSource(strings = {"productId", "id", "shopId", "ownerId", "vendorId", "createdBy", "creator", "ownership", "price", "scope"})
    void forgedFieldsAreRejectedOnEveryMutation(String field) throws Exception {
        authenticate(otherVendor, "VENDOR"); long foreignId = management.create(foreignProduct, form());
        long id = create(); var before = state();
        String forged = Long.toString(field.equals("productId") ? foreignProduct : field.equals("id") ? foreignId : otherVendor);
        for (String operation : List.of("create", "update", "disable")) {
            String url = operation.equals("create") ? base(product) : path(product, id, "/" + operation);
            var request = post(url).param("version", Long.toString(version(id))).param(field, forged);
            if (!operation.equals("disable")) {
                valid(request).with(http -> {
                    http.setParameter("name", "Forged attempt");
                    http.setParameter("discountPercent", "25");
                    if (operation.equals("create")) {
                        // Otherwise valid and adjacent: overlap must not conceal a missing tampering guard.
                        http.setParameter("startsAt", "2100-01-01T00:00");
                        http.setParameter("endsAt", "2101-01-01T00:00");
                    }
                    return http;
                });
                var response = mvc.perform(request.with(user(principal(vendor, "VENDOR"))).with(csrf()))
                        .andExpect(status().isOk()).andExpect(model().attributeHasErrors("promotionForm")).andReturn();
                var binding = (org.springframework.validation.BindingResult) response.getModelAndView().getModel()
                        .get(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX + "promotionForm");
                assertThat(binding.getGlobalErrors()).extracting(org.springframework.validation.ObjectError::getCode)
                        .contains("invalidFields");
            } else {
                mvc.perform(request.with(user(principal(vendor, "VENDOR"))).with(csrf()))
                        .andExpect(status().isBadRequest()).andExpect(view().name("vendor/promotions/error"));
            }
            assertThat(state()).as("unchanged after rejected %s with %s", operation, field).isEqualTo(before);
        }
    }
    @ParameterizedTest @CsvSource({"name,''", "discountPercent,0", "discountPercent,-1", "discountPercent,100.1", "discountPercent,garbage", "endsAt,2019-01-01T00:00", "startsAt,invalid"})
    void invalidFormsWriteNothing(String field, String value) throws Exception {
        var request = valid(post(base(product))).with(http -> { http.setParameter(field, value); return http; });
        mvc.perform(request.with(user(principal(vendor, "VENDOR"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("promotionForm"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions", Integer.class)).isZero();
    }
    @Test void foreignProductAndMismatchedPromotionPathsAreSafeNotFoundIncludingInvalidForms() throws Exception {
        long id = create(); long anotherProduct = product(shop, "1000");
        for (String url : List.of(base(foreignProduct), base(foreignProduct) + "/new", path(foreignProduct, id, "/edit"), path(anotherProduct, id, "/edit")))
            mvc.perform(get(url).with(user(principal(vendor, "VENDOR")))).andExpect(status().isNotFound());
        mvc.perform(valid(post(base(foreignProduct))).with(user(principal(vendor, "VENDOR"))).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(path(anotherProduct, id, "/update")).param("name", "").with(user(principal(vendor, "VENDOR"))).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(path(foreignProduct, id, "/disable")).param("version", "0").with(user(principal(vendor, "VENDOR"))).with(csrf())).andExpect(status().isNotFound());
        authenticate(otherVendor, "VENDOR"); rejects(() -> management.get(product, id), ErrorCode.RESOURCE_NOT_FOUND);
    }
    @Test void everyRouteEnforcesVendorAndEveryPostEnforcesCsrf() throws Exception {
        long id = create();
        for (String url : List.of(base(product), base(product) + "/new", path(product, id, "/edit"))) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            for (String role : List.of("USER", "ADMIN", "MANAGER", "SHIPPER"))
                mvc.perform(get(url).with(user(principal(buyer, role)))).andExpect(status().isForbidden());
        }
        for (String url : List.of(base(product), path(product, id, "/update"), path(product, id, "/disable"))) {
            mvc.perform(valid(post(url)).with(user(principal(vendor, "VENDOR")))).andExpect(status().isForbidden());
            mvc.perform(valid(post(url)).with(user(principal(buyer, "USER"))).with(csrf())).andExpect(status().isForbidden());
        }
    }
    @Test
    @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.BEFORE_METHOD)
    void realJwtAndRenderedCsrfCreateRoundTrip() throws Exception {
        var loginPage = mvc.perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse();
        var loginMatcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(loginPage.getContentAsString());
        assertThat(loginMatcher.find()).isTrue();
        var login = mvc.perform(post("/login").cookie(loginPage.getCookie("XSRF-TOKEN"))
                        .param("_csrf", loginMatcher.group(1)).param("identifier", username).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        var auth = login.getCookie("UTEEXPRESS_AUTH"); assertThat(auth).isNotNull();
        var page = mvc.perform(get(base(product) + "/new").cookie(auth)).andExpect(status().isOk()).andReturn().getResponse();
        var matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.getContentAsString());
        assertThat(matcher.find()).isTrue();
        mvc.perform(valid(post(base(product))).cookie(auth, page.getCookie("XSRF-TOKEN")).param("_csrf", matcher.group(1)))
                .andExpect(status().is3xxRedirection());
    }
    @Test void unapprovedShopCannotReadOrMutateAndHiddenProductDoesNotBecomePublic() {
        long id = create(); authenticate(vendor, "VENDOR");
        jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED',moderation_reason='Restricted' WHERE id=?", shop);
        rejects(() -> management.list(product), ErrorCode.ACCESS_DENIED);
        rejects(() -> management.disable(product, id, 0L), ErrorCode.ACCESS_DENIED);
        jdbc.update("UPDATE uteexpress.shops SET status='APPROVED' WHERE id=?", shop);
        jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", product);
        assertThat(catalog.findCartProducts(Set.of(product)).getFirst().purchasable()).isFalse();
        rejects(() -> publicCatalog.product(product), ErrorCode.RESOURCE_NOT_FOUND);
    }
    @Test void overlapCreateUpdateAndReenableGiveSafeFeedbackAndPreserveRows() throws Exception {
        long id = create(); var before = state();
        mvc.perform(valid(post(base(product))).with(user(principal(vendor, "VENDOR"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("promotionForm"));
        assertThat(state()).isEqualTo(before);
        authenticate(vendor, "VENDOR");
        var disabled = form(); disabled.setActive(false); long second = management.create(product, disabled);
        var update = updateForm(0L);
        rejects(() -> management.update(product, second, update), ErrorCode.CONFLICT);
        assertThat(management.get(product, second).active()).isFalse();
        management.disable(product, id, 0L); management.update(product, second, update);
        assertThat(management.get(product, second).active()).isTrue();
    }
    @ParameterizedTest @CsvSource({"2020-01-01,2100-01-01", "2025-01-01,2026-01-01", "2019-01-01,2021-01-01", "2099-01-01,2101-01-01"})
    void databaseRejectsEveryOverlappingShape(String start, String end) {
        insert(product, "10", "2020-01-01", "2100-01-01", true);
        assertThatThrownBy(() -> insert(product, "20", start, end, true)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void adjacencyDisabledAndOtherProductsAreAllowed() {
        insert(product, "10", "2020-01-01", "2021-01-01", true);
        insert(product, "20", "2021-01-01", "2022-01-01", true);
        insert(product, "30", "2020-01-01", "2022-01-01", false);
        insert(foreignProduct, "40", "2020-01-01", "2022-01-01", true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions", Integer.class)).isEqualTo(4);
    }
    @Test void updateChecksOverlapUsingTheSameMicrosecondPrecisionAsPersistence() {
        authenticate(vendor, "VENDOR"); var at = LocalDateTime.of(2020, 1, 2, 0, 0);
        var first = form(); first.setStartsAt(at.plusNanos(2000)); first.setEndsAt(at.plusNanos(4000)); management.create(product, first);
        var second = form(); second.setStartsAt(at.plusNanos(6000)); second.setEndsAt(at.plusNanos(8000));
        long id = management.create(product, second);
        var update = updateForm(0L); update.setStartsAt(at); update.setEndsAt(at.plusNanos(2999));
        management.update(product, id, update);
        assertThat(management.get(product, id).endsAt()).isEqualTo(at.plusNanos(2000));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "100.01", "NaN", "Infinity"})
    void databaseRejectsInvalidPercentage(String percent) {
        assertThatThrownBy(() -> insert(product, percent, "2020-01-01", "2100-01-01", true)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void databaseRejectsInvalidWindowAndForeignKeys() {
        for (String end : List.of("2019-01-01", "2020-01-01", "infinity"))
            assertThatThrownBy(() -> insert(product, "10", "2020-01-01", end, true)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert(Long.MAX_VALUE, "10", "2020-01-01", "2100-01-01", true)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @ParameterizedTest @CsvSource({"3,50", "101,12.5", "125000,12.123456789", "1,100", "99999999999999999,99.999999"})
    void sqlAndJavaPricingAgreeIncludingHalfDongAndPrecision(String base, String percent) {
        jdbc.update("UPDATE uteexpress.products SET price=? WHERE id=?", new BigDecimal(base), product);
        insert(product, percent, "2020-01-01", "2100-01-01", true);
        var projected = catalog.requirePurchasableProducts(Set.of(product)).getFirst();
        var expected = PromotionPricingService.calculate(new BigDecimal(base), new BigDecimal(percent));
        assertThat(projected.unitPrice()).isEqualByComparingTo(expected.basePrice());
        assertThat(projected.discountSnapshot()).isEqualByComparingTo(expected.discount());
        assertThat(projected.finalUnitPrice()).isEqualByComparingTo(expected.effectivePrice());
    }
    @Test void scheduleStartIsInclusiveEndExclusiveAndSingleInstantCoversBatch() {
        insert(product, "20", "2026-10-08T00:00:00Z", "2026-10-08T00:00:01Z", true);
        insert(foreignProduct, "10", "2026-10-08T00:00:01Z", "2026-10-08T00:00:02Z", true);
        assertThat(discountAt(product, "2026-10-07T23:59:59.999999Z")).isEqualByComparingTo("0");
        assertThat(discountAt(product, "2026-10-08T00:00:00Z")).isEqualByComparingTo("25000");
        assertThat(discountAt(product, "2026-10-08T00:00:01Z")).isEqualByComparingTo("0");
        var batch = jdbc.queryForList("SELECT product_id,discount FROM uteexpress.product_prices_at(?) WHERE product_id IN (?,?) ORDER BY product_id", Timestamp.from(Instant.parse("2026-10-08T00:00:01Z")), product, foreignProduct);
        assertThat((BigDecimal) batch.get(0).get("discount")).isEqualByComparingTo("0");
        assertThat((BigDecimal) batch.get(1).get("discount")).isEqualByComparingTo("12500");
    }
    @Test void catalogFilteringSortingPaginationAndAllBuyerDisplaysUseEffectivePrices() throws Exception {
        insert(product, "80", "2020-01-01", "2100-01-01", true);
        long cheaperBase = product(shop, "30000");
        var criteria = new ProductSearchCriteria(); criteria.setCategory(key); criteria.setSort("priceAsc"); criteria.setSize(1);
        var first = publicCatalog.search(criteria);
        assertThat(first.totalItems()).isEqualTo(3); assertThat(first.products().getFirst().id()).isEqualTo(product);
        assertThat(first.products().getFirst().price()).isEqualByComparingTo("125000");
        assertThat(first.products().getFirst().effectivePrice()).isEqualByComparingTo("25000");
        criteria.setPage(1); assertThat(publicCatalog.search(criteria).products().getFirst().id()).isEqualTo(cheaperBase);
        criteria.setPage(0); criteria.setMaxPrice(new BigDecimal("26000")); assertThat(publicCatalog.search(criteria).totalItems()).isEqualTo(1);
        assertThat(publicCatalog.product(product).effectivePrice()).isEqualByComparingTo("25000");
        assertThat(publicCatalog.category(key).products()).filteredOn(p -> p.id().equals(product)).extracting(ProductCard::effectivePrice)
                .containsExactly(new BigDecimal("25000.00"));
        assertThat(publicCatalog.shop(jdbc.queryForObject("SELECT slug FROM uteexpress.shops WHERE id=?", String.class, shop)).products())
                .filteredOn(p -> p.id().equals(product)).extracting(ProductCard::effectivePrice).containsExactly(new BigDecimal("25000.00"));
        authenticate(buyer, "USER");
        var cart = carts.getCurrentUserCart().orElseThrow(); assertThat(cart.subtotal()).isEqualByComparingTo("50000");
        assertThat(cart.items().getFirst().originalUnitPrice()).isEqualByComparingTo("125000");
        engagement.addFavorite(product); engagement.recordView(product);
        assertThat(engagement.favorites().getFirst().price()).isEqualByComparingTo("25000");
        assertThat(engagement.recent().getFirst().price()).isEqualByComparingTo("25000");
        mvc.perform(get("/products/" + product)).andExpect(status().isOk()).andExpect(content().string(containsString("<del")));
        mvc.perform(get("/user/cart/view").with(user(principal(buyer, "USER")))).andExpect(status().isOk()).andExpect(content().string(containsString("<del")));
    }
    @Test void checkoutAppliesVoucherAfterPromotionAndPreservesSnapshotsAndReplayAfterDisable() {
        long id = create(); voucher("PERCENTAGE", "10", "0"); authenticate(buyer, "USER");
        var quote = quotes.quote(new QuoteRequest(address, provider, "STANDARD", "SAVE")).quote();
        assertThat(quote.items().getFirst().discountSnapshot()).isEqualByComparingTo("25000");
        assertThat(quote.totals().subtotal()).isEqualByComparingTo("200000");
        assertThat(quote.totals().discountTotal()).isEqualByComparingTo("20000");
        assertThat(quote.totals().grandTotal()).isEqualByComparingTo("185000");
        var receipt = placement.placeOrder(request("SAVE"));
        var item = jdbc.queryForMap("SELECT * FROM uteexpress.order_items WHERE order_id=?", receipt.orderId());
        assertMoney(item, "unit_price", "125000"); assertMoney(item, "discount_snapshot", "25000");
        assertMoney(item, "final_unit_price", "100000"); assertMoney(item, "line_total", "200000");
        var order = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", receipt.orderId());
        assertMoney(order, "discount_total", "20000"); assertMoney(order, "commission_amount", "12822");
        assertThat(jdbc.queryForObject("SELECT amount FROM uteexpress.payments WHERE order_id=?", BigDecimal.class, receipt.orderId())).isEqualByComparingTo("185000");
        authenticate(vendor, "VENDOR"); management.disable(product, id, 0L);
        jdbc.update("UPDATE uteexpress.products SET price=500000 WHERE id=?", product);
        authenticate(buyer, "USER"); var replay = placement.placeOrder(request("SAVE"));
        assertThat(replay.replayed()).isTrue(); assertThat(replay.grandTotal()).isEqualByComparingTo("185000");
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.order_items WHERE order_id=?", receipt.orderId())).isEqualTo(item);
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", receipt.orderId())).isEqualTo(order);
        authenticate(vendor, "VENDOR");
        lifecycle.transition(new OrderTransitionCommand(receipt.orderId(), OrderStatus.NEW, vendorOrders.detail(receipt.orderId()).version(), OrderAction.CANCEL_NEW, "OUT_OF_STOCK"));
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.voucher_usages WHERE order_id=?", String.class, receipt.orderId())).isEqualTo("RELEASED");
        assertMoney(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", receipt.orderId()), "discount_total", "20000");
    }
    @Test void voucherMinimumSpendUsesPostPromotionSubtotal() {
        create(); voucher("FIXED", "10000", "220000"); authenticate(buyer, "USER"); var before = state();
        assertThatThrownBy(() -> placement.placeOrder(request("SAVE"))).isInstanceOf(ApplicationException.class);
        assertThat(state()).isEqualTo(before);
        jdbc.update("UPDATE uteexpress.vouchers SET min_subtotal=200000 WHERE code='SAVE'");
        assertThat(quotes.quote(new QuoteRequest(address, provider, "STANDARD", "SAVE")).quote().totals().grandTotal()).isEqualByComparingTo("195000");
    }
    @Test void checkoutRoundsPerUnitBeforeMultiplyingQuantityAndRendersAllPriceColumns() throws Exception {
        jdbc.update("UPDATE uteexpress.products SET price=3 WHERE id=?", product);
        insert(product, "50", "2020-01-01", "2100-01-01", true);
        var quote = quotes.quote(new QuoteRequest(address, provider, "STANDARD")).quote();
        assertThat(quote.items().getFirst().discountSnapshot()).isEqualByComparingTo("2");
        assertThat(quote.items().getFirst().finalUnitPrice()).isEqualByComparingTo("1");
        assertThat(quote.items().getFirst().lineTotal()).isEqualByComparingTo("2");
        mvc.perform(post("/user/checkout/view").with(user(principal(buyer, "USER"))).with(csrf())
                        .param("addressId", "" + address).param("shippingProviderId", "" + provider).param("shippingServiceCode", "STANDARD"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Giá sau giảm")))
                .andExpect(content().string(containsString("Giảm mỗi sản phẩm")));
    }
    @Test void overflowingPromotedLineRollsBackPlacement() {
        create(); jdbc.update("UPDATE uteexpress.products SET price=99999999999999999 WHERE id=?", product);
        authenticate(buyer, "USER"); var before = state();
        rejects(() -> placement.placeOrder(request(null)), ErrorCode.VALIDATION_FAILED);
        assertThat(state()).isEqualTo(before);
    }
    @Test void promotionsPreserveVoucherQuotaUnderCompetingBuyers() throws Exception {
        create(); voucher("PERCENTAGE", "10", "0"); jdbc.update("UPDATE uteexpress.vouchers SET total_limit=1 WHERE code='SAVE'");
        long second = account("second" + username, "USER");
        long secondAddress = jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Receiver','0900000000','VN','District','Detail',true) RETURNING id", Long.class, second);
        authenticate(second, "USER"); carts.addProduct(new AddCartProductRequest(product, 2));
        var command = new CheckoutRequest(UUID.randomUUID().toString(), List.of(new CheckoutRequest.Item(product, 2)), secondAddress,
                provider, "STANDARD", CheckoutRequest.PaymentMethod.COD, "SAVE");
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?>[] contender = new Future[1]; authenticate(buyer, "USER");
            var first = new TransactionTemplate(transactions).execute(status -> {
                var receipt = placement.placeOrder(request("SAVE"));
                contender[0] = pool.submit(() -> { authenticate(second, "USER"); return placement.placeOrder(command); });
                awaitLock("%products%"); return receipt;
            });
            assertThat(first.grandTotal()).isEqualByComparingTo("185000");
            assertThatThrownBy(() -> contender[0].get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(ApplicationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.orders", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.voucher_usages WHERE status='REDEEMED'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, product)).isEqualTo(8);
        }
    }
    @Test void fullDiscountCheckoutStillUsesShippingAndZeroMerchandiseCommission() {
        var form = form(); form.setDiscountPercent(new BigDecimal("100")); authenticate(vendor, "VENDOR"); management.create(product, form);
        authenticate(buyer, "USER"); var receipt = placement.placeOrder(request(null));
        assertThat(receipt.grandTotal()).isEqualByComparingTo("5000");
        var order = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", receipt.orderId());
        assertMoney(order, "subtotal", "0"); assertMoney(order, "commission_amount", "0");
    }
    @Test void previewIsAdvisoryAndPlacementRecalculatesChangedPromotion() {
        long id = create(); authenticate(buyer, "USER");
        assertThat(quotes.quote(new QuoteRequest(address, provider, "STANDARD")).quote().totals().subtotal()).isEqualByComparingTo("200000");
        authenticate(vendor, "VENDOR"); var form = updateForm(0L); form.setDiscountPercent(new BigDecimal("50")); management.update(product, id, form);
        authenticate(buyer, "USER"); assertThat(placement.placeOrder(request(null)).grandTotal()).isEqualByComparingTo("130000");
    }
    @Test void rollbackAfterAllPlacementEffectsLeavesStockQuotaCartAndSnapshotsUntouchedAndCanRetry() {
        create(); voucher("PERCENTAGE", "10", "0"); authenticate(buyer, "USER"); var before = state();
        CartService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(carts);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected rollback"); }).when(target).removeCheckedOutItems(anyList());
        assertThatThrownBy(() -> placement.placeOrder(request("SAVE"))).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo(before); reset(target);
        assertThat(placement.placeOrder(request("SAVE")).grandTotal()).isEqualByComparingTo("185000");
    }
    @Test void managementOuterRollbackRemovesScheduleAndAllowsRetry() {
        authenticate(vendor, "VENDOR");
        new TransactionTemplate(transactions).executeWithoutResult(status -> { management.create(product, form()); status.setRollbackOnly(); });
        assertThat(management.list(product)).isEmpty(); assertThat(management.create(product, form())).isPositive();
    }
    @Test void concurrentDirectSqlOverlapWritersCannotBothCommit() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<Long>[] contender = new Future[1];
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                insert(product, "20", "2020-01-01", "2100-01-01", true);
                contender[0] = pool.submit(() -> insert(product, "30", "2020-01-01", "2100-01-01", true));
                awaitLock("%promotions%"); assertThat(contender[0].isDone()).isFalse();
            });
            assertThatThrownBy(() -> contender[0].get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions", Integer.class)).isEqualTo(1);
        }
    }
    @Test void productLockSerializesApplicationCreatesIncludingEmptySchedule() throws Exception {
        // One owner per Shop prevents two eligible Vendors sharing this Product. A Product-only
        // holder isolates its row lock without holding any Account, Shop or Promotion lock.
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<Long>[] contender = new Future[1];
            var contenderBackend = new CompletableFuture<Integer>();
            authenticate(vendor, "VENDOR");
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                int holder = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                assertThat(jdbc.queryForObject("SELECT id FROM uteexpress.products WHERE id=? FOR UPDATE", Long.class, product))
                        .isEqualTo(product);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions WHERE product_id=?", Integer.class, product)).isZero();
                contender[0] = pool.submit(() -> {
                    authenticate(vendor, "VENDOR");
                    try {
                        return new TransactionTemplate(transactions).execute(worker -> {
                            contenderBackend.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            return management.create(product, form());
                        });
                    } finally { SecurityContextHolder.clearContext(); }
                });
                awaitProductLock(contenderBackend.orTimeout(5, TimeUnit.SECONDS).join(), holder);
                assertThat(contender[0].isDone()).isFalse();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions WHERE product_id=?", Integer.class, product)).isZero();
            });
            long id = contender[0].get(15, TimeUnit.SECONDS);
            assertThat(management.list(product)).hasSize(1);
            assertThat(management.get(product, id).active()).isTrue();
            assertThat(jdbc.queryForMap("SELECT product_id,created_by FROM uteexpress.promotions WHERE id=?", id))
                    .containsEntry("product_id", product).containsEntry("created_by", vendor);
        }
    }
    @Test void accountLockSerializesConcurrentCreatesAndRejectsOverlap() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<Long>[] contender = new Future[1];
            authenticate(vendor, "VENDOR");
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                management.create(product, form());
                contender[0] = pool.submit(() -> { authenticate(vendor, "VENDOR"); return management.create(product, form()); });
                awaitLock("%users%"); assertThat(contender[0].isDone()).isFalse();
            });
            assertThatThrownBy(() -> contender[0].get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(ApplicationException.class);
            assertThat(management.list(product)).hasSize(1);
        }
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void checkoutWaitsForScheduleInsertThenUsesItsCommitOrRollback(boolean commit) throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<com.uteexpress.order.dto.PlaceOrderResult>[] checkout = new Future[1];
            authenticate(vendor, "VENDOR");
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                management.create(product, form());
                checkout[0] = pool.submit(() -> { authenticate(buyer, "USER"); return placement.placeOrder(request(null)); });
                awaitLock("%products%"); assertThat(checkout[0].isDone()).isFalse();
                if (!commit) status.setRollbackOnly();
            });
            assertThat(checkout[0].get(15, TimeUnit.SECONDS).grandTotal()).isEqualByComparingTo(commit ? "205000" : "255000");
        }
    }
    @Test void managementWaitsForCheckoutThenCannotChangeItsHistoricalPrice() throws Exception {
        long id = create();
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?>[] mutation = new Future[1];
            authenticate(buyer, "USER");
            var receipt = new TransactionTemplate(transactions).execute(status -> {
                var placed = placement.placeOrder(request(null));
                mutation[0] = pool.submit(() -> { authenticate(vendor, "VENDOR"); management.disable(product, id, 0L); });
                awaitLock("%products%"); assertThat(mutation[0].isDone()).isFalse(); return placed;
            });
            mutation[0].get(15, TimeUnit.SECONDS);
            assertMoney(jdbc.queryForMap("SELECT * FROM uteexpress.order_items WHERE order_id=?", receipt.orderId()), "final_unit_price", "100000");
            assertThat(catalog.requirePurchasableProducts(Set.of(product)).getFirst().finalUnitPrice()).isEqualByComparingTo("125000");
        }
    }

    private long account(String name, String role) {
        long id = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class,
                name + "@example.test", name + "@example.test", name, name, passwords.encode(PASSWORD));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role); return id;
    }
    private long shop(long owner) { return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, owner, UUID.randomUUID().toString()); }
    private long product(long targetShop, String price) { return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',?,10,'ACTIVE') RETURNING id", Long.class, targetShop, category, new BigDecimal(price)); }
    private long create() { authenticate(vendor, "VENDOR"); return management.create(product, form()); }
    private VendorPromotionForm form() {
        var form = new VendorPromotionForm(); form.setName("Sale"); form.setDiscountPercent(new BigDecimal("20"));
        form.setStartsAt(LocalDateTime.of(2020, 1, 2, 0, 0)); form.setEndsAt(LocalDateTime.of(2100, 1, 1, 0, 0)); return form;
    }
    private VendorPromotionUpdateForm updateForm(long version) {
        var form = new VendorPromotionUpdateForm(); var original = form(); form.setName(original.getName());
        form.setDiscountPercent(original.getDiscountPercent()); form.setStartsAt(original.getStartsAt()); form.setEndsAt(original.getEndsAt()); form.setVersion(version); return form;
    }
    private long insert(long target, String percent, String start, String end, boolean active) {
        return jdbc.queryForObject("INSERT INTO uteexpress.promotions(product_id,name,discount_percent,starts_at,ends_at,active,created_by) VALUES (?,'SQL sale',?::numeric,?::timestamptz,?::timestamptz,?,?) RETURNING id", Long.class, target, percent, start, end, active, vendor);
    }
    private BigDecimal discountAt(long target, String instant) { return jdbc.queryForObject("SELECT discount FROM uteexpress.product_prices_at(?) WHERE product_id=?", BigDecimal.class, Timestamp.from(Instant.parse(instant)), target); }
    private long version(long id) { return jdbc.queryForObject("SELECT version FROM uteexpress.promotions WHERE id=?", Long.class, id); }
    private static MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder request) { return request.param("name", "Sale").param("discountPercent", "20").param("startsAt", "2020-01-02T00:00").param("endsAt", "2100-01-01T00:00").param("active", "true"); }
    private static String base(long product) { return "/vendor/products/" + product + "/promotions"; }
    private static String path(long product, long id, String suffix) { return base(product) + "/" + id + suffix; }
    private static UteExpressPrincipal principal(long id, String role) { return new UteExpressPrincipal(id, "actor" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true); }
    private static void authenticate(long id, String role) { var p = principal(id, role); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities())); }
    private void voucher(String type, String value, String minimum) {
        jdbc.update("INSERT INTO uteexpress.vouchers(code,scope,shop_id,type,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,active,created_by) VALUES ('SAVE','SHOP',?,?,?::numeric,?::numeric,'2020-01-01','2100-01-01',10,2,true,?)", shop, type, value, minimum, vendor);
    }
    private CheckoutRequest request(String voucher) { return new CheckoutRequest(key, List.of(new CheckoutRequest.Item(product, 2)), address, provider, "STANDARD", CheckoutRequest.PaymentMethod.COD, voucher); }
    private void rejects(Runnable action, ErrorCode code) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ApplicationException.class, error -> assertThat(error.errorCode()).isEqualTo(code)); }
    private static void assertMoney(Map<String, Object> row, String column, String value) { assertThat((BigDecimal) row.get(column)).isEqualByComparingTo(value); }
    private Map<String, List<Map<String, Object>>> state() {
        var result = new TreeMap<String, List<Map<String, Object>>>();
        for (String table : List.of("promotions", "vouchers", "voucher_usages", "orders", "order_items", "order_status_history", "payments", "products", "carts", "cart_items"))
            result.put(table, jdbc.queryForList("SELECT * FROM uteexpress." + table + " ORDER BY id"));
        return result;
    }
    private void awaitLock(String relation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()"); // PostgreSQL caches activity snapshots within a transaction.
            if (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE ?", Integer.class, relation) > 0) return;
            try { Thread.sleep(20); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
        }
        throw new AssertionError("No observed PostgreSQL lock wait for " + relation);
    }
    private void awaitProductLock(int contender, int holder) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                     WHERE pid=? AND datname=current_database() AND wait_event_type='Lock'
                       AND query ILIKE '%products%' AND ? = ANY(pg_blocking_pids(pid)))
                    """, Boolean.class, contender, holder))) return;
            try { Thread.sleep(20); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
        }
        throw new AssertionError("No Product lock wait from backend " + contender + " blocked by " + holder);
    }
}
