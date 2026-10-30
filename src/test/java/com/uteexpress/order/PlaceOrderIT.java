package com.uteexpress.order;

import com.uteexpress.cart.dto.*;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.catalog.service.DatabaseInventoryService;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import com.uteexpress.common.exception.*;
import com.uteexpress.governance.service.CategoryService;
import com.uteexpress.governance.service.ShopModerationService;
import com.uteexpress.order.dto.PlaceOrderResult;
import com.uteexpress.order.service.OrderPlacementService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PlaceOrderIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired OrderPlacementService placement;
    @MockitoSpyBean CheckoutQuoteService quotes;
    @MockitoSpyBean CartService carts;
    @MockitoSpyBean DatabaseInventoryService inventory;
    @Autowired ShopModerationService shopModeration;
    @Autowired CategoryService categoryService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    long buyer, other, address, otherAddress, shop, category, product, item, provider, policy;
    String key;

    @BeforeEach void fixture() {
        for (String table : List.of("order_status_history", "payments", "order_items", "orders", "commission_policies")) {
            jdbc.update("DELETE FROM uteexpress." + table);
        }
        key = UUID.randomUUID().toString();
        buyer = createUser();
        other = createUser();
        address = createAddress(buyer);
        otherAddress = createAddress(other);
        shop = createShop(buyer);
        category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id",
                Long.class, key);
        product = createProduct(shop, 125000);
        provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id",
                Long.class, "P" + key.replace("-", "").substring(0, 18).toUpperCase());
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',17000,true)", provider);
        policy = jdbc.queryForObject("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7.1234,'2020-01-01',?) RETURNING id",
                Long.class, buyer);
        authenticate(buyer);
        item = carts.addProduct(new AddCartProductRequest(product, 2)).items().getFirst().id();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void createsExactlyOneOrderWithFreshImmutableSnapshotsAndSelectiveCleanup() {
        long second = createProduct(shop, 100);
        carts.addProduct(new AddCartProductRequest(second, 3));
        long unselected = createProduct(shop, 500);
        deselectAdded(unselected);
        long foreignShopProduct = createProduct(createShop(other), 200);
        deselectAdded(foreignShopProduct);
        jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", foreignShopProduct);
        quotes.quote(new QuoteRequest(address, provider, "STANDARD"));
        jdbc.update("UPDATE uteexpress.products SET price=130000,name='Current name' WHERE id=?", product);
        jdbc.update("UPDATE uteexpress.shipping_rates SET fee=21000 WHERE provider_id=?", provider);
        jdbc.update("UPDATE uteexpress.addresses SET detail='Updated address' WHERE id=?", address);
        var command = request(List.of(new CheckoutRequest.Item(product, 2), new CheckoutRequest.Item(second, 3)));
        var result = placement.placeOrder(command);
        assertThat(result.replayed()).isFalse();
        assertThat(result.status().name()).isEqualTo("NEW");
        assertThat(result.grandTotal()).isEqualByComparingTo("281300");
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("order_items")).isEqualTo(2);
        assertThat(count("payments")).isEqualTo(1);
        var payment = jdbc.queryForMap("SELECT * FROM uteexpress.payments WHERE order_id=?", result.orderId());
        assertThat(payment).containsEntry("method", "COD").containsEntry("status", "UNPAID")
                .containsEntry("paid_at", null).containsEntry("provider_reference", null);
        assertThat((BigDecimal) payment.get("amount")).isEqualByComparingTo(result.grandTotal());
        var order = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", result.orderId());
        assertThat(order).containsEntry("buyer_id", buyer).containsEntry("shop_id", shop)
                .containsEntry("detail", "Updated address").containsEntry("commission_policy_id", policy)
                .containsEntry("checkout_key", key).containsEntry("status", "NEW")
                .containsEntry("shipping_provider_id", provider).containsEntry("shipping_service_code", "STANDARD");
        assertThat((BigDecimal) order.get("subtotal")).isEqualByComparingTo("260300");
        assertThat((BigDecimal) order.get("discount_total")).isEqualByComparingTo("0");
        assertThat((BigDecimal) order.get("shipping_fee")).isEqualByComparingTo("21000");
        assertThat((BigDecimal) order.get("commission_rate_snapshot")).isEqualByComparingTo("7.1234");
        assertThat((BigDecimal) order.get("commission_amount")).isEqualByComparingTo("18542");
        var lines = jdbc.queryForList("SELECT * FROM uteexpress.order_items ORDER BY product_id");
        assertThat(lines).anySatisfy(line -> {
            assertThat(line).containsEntry("product_id", product).containsEntry("quantity", 2)
                    .containsEntry("product_name_snapshot", "Current name");
            assertThat((BigDecimal) line.get("unit_price")).isEqualByComparingTo("130000");
            assertThat((BigDecimal) line.get("line_total")).isEqualByComparingTo("260000");
        });
        assertThat(lines).anySatisfy(line -> {
            assertThat(line).containsEntry("product_id", second).containsEntry("quantity", 3);
            assertThat((BigDecimal) line.get("line_total")).isEqualByComparingTo("300");
        });
        var history = jdbc.queryForMap("SELECT * FROM uteexpress.order_status_history");
        assertThat(history).containsEntry("from_status", null).containsEntry("to_status", "NEW").containsEntry("actor_id", buyer);
        assertThat(history.get("created_at")).isEqualTo(order.get("created_at")).isEqualTo(order.get("updated_at"));
        assertThat(stock(product)).isEqualTo(8);
        assertThat(stock(second)).isEqualTo(7);
        assertThat(carts.getCurrentUserCart().orElseThrow().items()).extracting(CartItemView::productId)
                .containsExactly(unselected, foreignShopProduct);
        jdbc.update("UPDATE uteexpress.products SET price=999,name='Later name' WHERE id=?", product);
        jdbc.update("UPDATE uteexpress.addresses SET detail='Later address' WHERE id=?", address);
        jdbc.update("UPDATE uteexpress.shipping_rates SET fee=99000,active=false WHERE provider_id=?", provider);
        jdbc.update("UPDATE uteexpress.shipping_providers SET active=false WHERE id=?", provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (9,'2021-01-01',?)", buyer);
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", result.orderId())).isEqualTo(order);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.order_items ORDER BY product_id")).isEqualTo(lines);
    }

    @Test void persistsShippingFactsFromFreshServerQuoteRatherThanRawRequestFields() {
        long validatedProvider = jdbc.queryForObject(
                "INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Other provider',true) RETURNING id",
                Long.class, "Q" + key.replace("-", "").substring(0, 18).toUpperCase());
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'EXPRESS_24','VN',23000,true)",
                validatedProvider);
        // Test the consumer boundary with a different selection returned by a real server quote.
        // The delegated quote still validates configuration and locks inventory inside placement.
        doAnswer(invocation -> quotes.quote(new QuoteRequest(address, validatedProvider, "EXPRESS_24")))
                .when(quotes).quote(new QuoteRequest(address, provider, "STANDARD"));
        var result = placement.placeOrder(request());
        var stored = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", result.orderId());
        assertThat(stored).containsEntry("shipping_provider_id", validatedProvider)
                .containsEntry("shipping_service_code", "EXPRESS_24");
        assertThat((BigDecimal) stored.get("shipping_fee")).isEqualByComparingTo("23000");
        assertThat(result.grandTotal()).isEqualByComparingTo("273000");
    }

    @Test void replayDoesNotReadChangedCheckoutStateOrClearNewCartData() {
        var command = request();
        var first = placement.placeOrder(command);
        carts.addProduct(new AddCartProductRequest(product, 1));
        jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", product);
        jdbc.update("DELETE FROM uteexpress.addresses WHERE id=?", address);
        jdbc.update("UPDATE uteexpress.commission_policies SET active=false WHERE id=?", policy);
        jdbc.update("DELETE FROM uteexpress.shipping_rates WHERE provider_id=?", provider);
        jdbc.update("DELETE FROM uteexpress.shipping_providers WHERE id=?", provider);
        var before = databaseState();
        var replay = placement.placeOrder(new CheckoutRequest(" " + key + " ", command.items(), address, provider,
                "STANDARD", CheckoutRequest.PaymentMethod.COD, " "));
        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(replay.createdAt()).isEqualTo(first.createdAt());
        assertThat(replay.replayed()).isTrue();
        assertThat(databaseState()).isEqualTo(before);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", first.orderId()))
                .containsEntry("shipping_provider_id", provider).containsEntry("shipping_service_code", "STANDARD");
        verify(quotes, times(1)).quote(any(QuoteRequest.class));
        verify(inventory, times(1)).decrease(anyList());
        verify(carts, times(1)).removeCheckedOutItems(anyList());
    }

    @Test void changedPayloadWithSameKeyConflictsBeforeCartAccess() {
        placement.placeOrder(request());
        var before = databaseState();
        rejects(() -> placement.placeOrder(request(List.of(new CheckoutRequest.Item(product, 1)))), ErrorCode.CONFLICT);
        rejects(() -> placement.placeOrder(new CheckoutRequest(key, request().items(), address, provider,
                "EXPRESS", CheckoutRequest.PaymentMethod.COD, null)), ErrorCode.CONFLICT);
        rejects(() -> placement.placeOrder(new CheckoutRequest(key, request().items(), address, provider + 1,
                "STANDARD", CheckoutRequest.PaymentMethod.COD, null)), ErrorCode.CONFLICT);
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test void historicalCheckoutReplayDoesNotBackfillShippingSelectionOrMissingPayment() {
        var command = request();
        var first = placement.placeOrder(command);
        // Simulate a pre-PAY-01 / pre-ORD-04 order with absent historical facts.
        jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?", first.orderId());
        jdbc.update("UPDATE uteexpress.orders SET shipping_provider_id=NULL,shipping_service_code=NULL WHERE id=?", first.orderId());
        var before = databaseState();
        assertThat(placement.placeOrder(command).replayed()).isTrue();
        assertThat(databaseState()).isEqualTo(before);
        assertThat(count("payments")).isZero();
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", first.orderId()))
                .containsEntry("shipping_provider_id", null).containsEntry("shipping_service_code", null);
    }

    @Test void checkoutKeyIsScopedToAuthenticatedBuyer() {
        var first = placement.placeOrder(request());
        authenticate(other);
        carts.addProduct(new AddCartProductRequest(product, 2));
        var second = placement.placeOrder(new CheckoutRequest(key, request().items(), otherAddress, provider,
                "STANDARD", CheckoutRequest.PaymentMethod.COD, null));
        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(count("orders")).isEqualTo(2);
        assertThat(stock(product)).isEqualTo(6);
    }

    @Test void foreignAddressRejectedWithoutMutation() {
        var before = databaseState();
        rejects(() -> placement.placeOrder(new CheckoutRequest(key, request().items(), otherAddress, provider,
                "STANDARD", CheckoutRequest.PaymentMethod.COD, null)), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test void mixedShopSelectionConflictsWithoutSplitting() {
        long second = createProduct(createShop(other), 100);
        carts.addProduct(new AddCartProductRequest(second, 1));
        var before = databaseState();
        rejects(() -> placement.placeOrder(request(List.of(new CheckoutRequest.Item(product, 2),
                new CheckoutRequest.Item(second, 1)))), ErrorCode.CONFLICT);
        assertThat(databaseState()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"empty", "unselected", "changedQuantity", "partialSelection", "foreignCart"})
    void cartStateMustExactlyMatchSubmittedSelection(String state) {
        var command = request();
        switch (state) {
            case "empty" -> carts.removeItem(item);
            case "unselected" -> carts.selectItem(item, new SelectCartItemRequest(false));
            case "changedQuantity" -> carts.updateQuantity(item, new UpdateCartQuantityRequest(3));
            case "partialSelection" -> carts.addProduct(new AddCartProductRequest(createProduct(shop, 100), 1));
            case "foreignCart" -> authenticate(other);
        }
        var before = databaseState();
        assertThatThrownBy(() -> placement.placeOrder(command)).isInstanceOf(ApplicationException.class);
        assertThat(databaseState()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"hidden", "moderated", "stock", "suspended", "category", "shipping", "missingPolicy", "inactivePolicy", "inactiveBuyer"})
    void invalidCurrentStateRollsBackWholePlacement(String state) {
        switch (state) {
            case "hidden" -> jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?", product);
            case "moderated" -> jdbc.update("UPDATE uteexpress.products SET status='MODERATED',moderation_reason='Test' WHERE id=?", product);
            case "stock" -> jdbc.update("UPDATE uteexpress.products SET stock=1 WHERE id=?", product);
            case "suspended" -> jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED',moderation_reason='Test' WHERE id=?", shop);
            case "category" -> jdbc.update("UPDATE uteexpress.categories SET active=false WHERE id=?", category);
            case "shipping" -> jdbc.update("UPDATE uteexpress.shipping_rates SET active=false WHERE provider_id=?", provider);
            case "missingPolicy" -> jdbc.update("DELETE FROM uteexpress.commission_policies");
            case "inactivePolicy" -> jdbc.update("UPDATE uteexpress.commission_policies SET active=false WHERE id=?", policy);
            case "inactiveBuyer" -> jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", buyer);
        }
        var before = databaseState();
        assertThatThrownBy(() -> placement.placeOrder(request())).isInstanceOf(ApplicationException.class)
                .satisfies(error -> {
                    if (state.endsWith("Policy")) assertThat(((ApplicationException) error).errorCode()).isEqualTo(ErrorCode.CONFLICT);
                });
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test void latestEffectivePolicyAtServerCheckoutTimeIsSnapshotted() {
        long effective = jdbc.queryForObject("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (8.5555,'2021-01-01',?) RETURNING id", Long.class, buyer);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (99,'2200-01-01',?)", buyer);
        var result = placement.placeOrder(request());
        var order = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", result.orderId());
        assertThat(order).containsEntry("commission_policy_id", effective);
        assertThat((BigDecimal) order.get("commission_rate_snapshot")).isEqualByComparingTo("8.5555");
        assertThat((BigDecimal) order.get("commission_amount")).isEqualByComparingTo("21389");
    }

    @Test void failureAfterStockOrderItemsHistoryAndCleanupRollsBackAndSameKeyCanRetry() {
        long second = createProduct(shop, 100);
        carts.addProduct(new AddCartProductRequest(second, 1));
        var command = request(List.of(new CheckoutRequest.Item(product, 2), new CheckoutRequest.Item(second, 1)));
        var before = databaseState();
        CartService cartTarget = AopTestUtils.getUltimateTargetObject(carts);
        doAnswer(call -> {
            call.callRealMethod();
            assertThat(count("orders")).isEqualTo(1);
            assertThat(count("order_items")).isEqualTo(2);
            assertThat(count("order_status_history")).isEqualTo(1);
            assertThat(count("payments")).isEqualTo(1);
            assertThat(stock(product)).isEqualTo(8);
            assertThat(carts.getCurrentUserCart().orElseThrow().items()).isEmpty();
            throw new ApplicationException(ErrorCode.CONFLICT);
        }).when(cartTarget).removeCheckedOutItems(anyList());
        rejects(() -> placement.placeOrder(command), ErrorCode.CONFLICT);
        assertThat(databaseState()).isEqualTo(before);
        doCallRealMethod().when(cartTarget).removeCheckedOutItems(anyList());
        assertThat(placement.placeOrder(command).replayed()).isFalse();
        assertThat(count("orders")).isEqualTo(1);
    }

    @Test void enclosingTransactionRollbackAlsoUndoesPlacement() {
        var before = databaseState();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            placement.placeOrder(request());
            tx.setRollbackOnly();
        });
        assertThat(databaseState()).isEqualTo(before);
        assertThat(placement.placeOrder(request()).replayed()).isFalse();
    }

    @Test void concurrentDuplicateSubmissionsReturnOneOrderAndDecrementOnce() throws Exception {
        var results = concurrent(buyer, request(), buyer, request());
        assertThat(results).allMatch(PlaceOrderResult.class::isInstance);
        var first = (PlaceOrderResult) results.get(0);
        var second = (PlaceOrderResult) results.get(1);
        assertThat(first.orderId()).isEqualTo(second.orderId());
        assertThat(List.of(first.replayed(), second.replayed())).containsExactlyInAnyOrder(true, false);
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("order_items")).isEqualTo(1);
        assertThat(count("order_status_history")).isEqualTo(1);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(stock(product)).isEqualTo(8);
    }

    @Test void concurrentCompetingBuyersCannotOversell() throws Exception {
        authenticate(other);
        carts.addProduct(new AddCartProductRequest(product, 2));
        jdbc.update("UPDATE uteexpress.products SET stock=3 WHERE id=?", product);
        var second = new CheckoutRequest(key, request().items(), otherAddress, provider, "STANDARD", CheckoutRequest.PaymentMethod.COD, null);
        var results = concurrent(buyer, request(), other, second);
        assertThat(results.stream().filter(PlaceOrderResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(ApplicationException.class::isInstance)).singleElement()
                .satisfies(error -> assertThat(((ApplicationException) error).errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(stock(product)).isEqualTo(1);
        assertThat(count("orders")).isEqualTo(1);
        long winner = jdbc.queryForObject("SELECT buyer_id FROM uteexpress.orders", Long.class);
        authenticate(winner);
        assertThat(carts.getCurrentUserCart().orElseThrow().items()).isEmpty();
        authenticate(winner == buyer ? other : buyer);
        assertThat(carts.getCurrentUserCart().orElseThrow().items()).singleElement()
                .satisfies(line -> assertThat(line.quantity()).isEqualTo(2));
    }

    @ParameterizedTest @ValueSource(strings = {"shop", "category"})
    void availabilityMutationWaitsUntilPlacementUsingLockedStateCommits(String target) throws Exception {
        CountDownLatch beforeDecrease = new CountDownLatch(1);
        CountDownLatch allowDecrease = new CountDownLatch(1);
        DatabaseInventoryService inventoryTarget = AopTestUtils.getUltimateTargetObject(inventory);
        doAnswer(call -> {
            beforeDecrease.countDown();
            if (!allowDecrease.await(10, TimeUnit.SECONDS)) throw new AssertionError("Placement release timed out");
            return call.callRealMethod();
        }).when(inventoryTarget).decrease(anyList());

        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<PlaceOrderResult> placing = pool.submit(() -> {
                authenticate(buyer);
                try { return placement.placeOrder(request()); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(beforeDecrease.await(10, TimeUnit.SECONDS))
                    .as("checkout reached stock mutation after locking availability rows").isTrue();

            CountDownLatch mutationStarted = new CountDownLatch(1);
            Future<?> changing = pool.submit(() -> {
                authenticateAdmin(other);
                mutationStarted.countDown();
                try {
                    if (target.equals("shop")) {
                        long version = jdbc.queryForObject("SELECT version FROM uteexpress.shops WHERE id=?", Long.class, shop);
                        shopModeration.change(shop, version, true, "Checkout concurrency regression");
                    } else {
                        long version = jdbc.queryForObject("SELECT version FROM uteexpress.categories WHERE id=?", Long.class, category);
                        categoryService.setActive(category, version, false);
                    }
                } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(mutationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertAvailabilityMutationWaiting(target.equals("shop") ? "shops" : "categories");
            assertThat(placing.isDone()).isFalse();

            allowDecrease.countDown();
            var placed = placing.get(10, TimeUnit.SECONDS);
            changing.get(10, TimeUnit.SECONDS);
            assertThat(placed.replayed()).isFalse();
            assertThat(count("orders")).isEqualTo(1);
            assertThat(stock(product)).isEqualTo(8);
            if (target.equals("shop")) {
                assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shops WHERE id=?", String.class, shop))
                        .isEqualTo("SUSPENDED");
            } else {
                assertThat(jdbc.queryForObject("SELECT active FROM uteexpress.categories WHERE id=?", Boolean.class, category))
                        .isFalse();
            }
        } finally {
            allowDecrease.countDown();
        }
    }

    @Test void sparseHtmlItemIndexesAreRejectedWithoutCheckoutEffects() throws Exception {
        clear();
        var before = databaseState();
        mvc.perform(post("/user/checkout/view/place-order").with(user(principal(buyer))).with(csrf())
                .param("checkoutKey", key).param("addressId", Long.toString(address))
                .param("shippingProviderId", Long.toString(provider)).param("shippingServiceCode", "STANDARD")
                .param("paymentMethod", "COD").param("items[1].productId", Long.toString(product))
                .param("items[1].quantity", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/user/checkout/view"))
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.notNullValue()));
        assertThat(databaseState()).isEqualTo(before);
        assertThat(count("orders")).isZero();
    }

    @Test void jsonCreatesThenReplaysWithCorrectStatus() throws Exception {
        clear();
        mvc.perform(post("/user/checkout/place-order").with(user(principal(buyer))).with(csrf())
                .contentType("application/json").content(body())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NEW")).andExpect(jsonPath("$.replayed").value(false));
        mvc.perform(post("/user/checkout/place-order").with(user(principal(buyer))).with(csrf())
                .contentType("application/json").content(body())).andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));
        assertThat(count("orders")).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"buyerId", "userId", "shopId", "price", "subtotal", "shippingFee", "grandTotal", "commissionPolicyId", "commissionRateSnapshot", "commissionAmount", "status", "paymentStatus", "shipperId", "collectedAmount", "requestHash"})
    void jsonCannotInjectServerOwnedValues(String field) throws Exception {
        clear();
        var before = databaseState();
        mvc.perform(post("/user/checkout/place-order").with(user(principal(buyer))).with(csrf())
                .contentType("application/json").content(body().replaceFirst("}$", ",\"" + field + "\":1}")))
                .andExpect(status().isBadRequest());
        assertThat(databaseState()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"zeroQuantity", "emptyItems", "duplicate", "noKey", "longKey", "online", "voucher", "itemPrice"})
    void invalidOrUnsupportedRequestRejected(String invalid) throws Exception {
        String json = body();
        json = switch (invalid) {
            case "zeroQuantity" -> json.replace("\"quantity\":2", "\"quantity\":0");
            case "emptyItems" -> json.replace("[{\"productId\":" + product + ",\"quantity\":2}]", "[]");
            case "duplicate" -> json.replace("}],", "},{\"productId\":" + product + ",\"quantity\":2}],");
            case "noKey" -> json.replace(key, " ");
            case "longKey" -> json.replace(key, "x".repeat(256));
            case "online" -> json.replace("COD", "ONLINE");
            case "voucher" -> json.replaceFirst("}$", ",\"voucherCode\":\"SAVE\"}");
            case "itemPrice" -> json.replace("\"quantity\":2", "\"quantity\":2,\"unitPrice\":1");
            default -> throw new AssertionError(invalid);
        };
        clear();
        var before = databaseState();
        mvc.perform(post("/user/checkout/place-order").with(user(principal(buyer))).with(csrf())
                .contentType("application/json").content(json)).andExpect(status().isBadRequest());
        assertThat(databaseState()).isEqualTo(before);
    }

    @Test void authenticationRoleAndCsrfAreRequired() throws Exception {
        clear();
        mvc.perform(post("/user/checkout/place-order").with(csrf()).contentType("application/json").content(body()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/user/checkout/place-order").with(user(principal(buyer))).contentType("application/json").content(body()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/checkout/view/place-order").with(user(principal(buyer))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/checkout/place-order").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body())).andExpect(status().isForbidden());
        assertThat(count("orders")).isZero();
    }

    @Test void htmlFormBindsSelectionEscapesReceiptAndReplaysWithoutAdditionalEffects() throws Exception {
        clear();
        var submit = post("/user/checkout/view/place-order").with(user(principal(buyer))).with(csrf())
                .param("checkoutKey", key).param("addressId", Long.toString(address))
                .param("shippingProviderId", Long.toString(provider)).param("shippingServiceCode", "STANDARD")
                .param("paymentMethod", "COD").param("items[0].productId", Long.toString(product)).param("items[0].quantity", "2")
                .param("buyerId", Long.toString(other)).param("grandTotal", "1").param("commissionAmount", "0")
                .param("paymentStatus", "PAID").param("collectedAmount", "267000").param("shipperId", Long.toString(buyer));
        var response = mvc.perform(submit).andExpect(redirectedUrl("/user/checkout/view"))
                .andExpect(flash().attributeExists("placedOrder")).andReturn();
        var receipt = (PlaceOrderResult) response.getFlashMap().get("placedOrder");
        assertThat(receipt.grandTotal()).isEqualByComparingTo("267000");
        mvc.perform(get("/user/checkout/view").with(user(principal(buyer))).flashAttrs(response.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(receipt.orderCode())));
        mvc.perform(submit).andExpect(redirectedUrl("/user/checkout/view"))
                .andExpect(flash().attributeExists("placedOrder"));
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT method,status,amount FROM uteexpress.payments"))
                .containsEntry("method", "COD").containsEntry("status", "UNPAID");
        assertThat(stock(product)).isEqualTo(8);
    }

    private List<Object> concurrent(long firstBuyer, CheckoutRequest first, long secondBuyer, CheckoutRequest second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                long owner = index == 0 ? firstBuyer : secondBuyer;
                CheckoutRequest command = index == 0 ? first : second;
                futures.add(pool.submit(() -> {
                    authenticate(owner);
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timed out");
                        return placement.placeOrder(command);
                    } catch (ApplicationException error) { return error; }
                    finally { SecurityContextHolder.clearContext(); }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.products WHERE id=? FOR UPDATE", Long.class, product);
                start.countDown();
                // Force real overlap: both requests must be waiting on database locks before releasing stock.
                // Duplicates wait on buyer/product; competing buyers both wait on product.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                int waiting = 0;
                while (waiting < 2 && System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'", Integer.class);
                    if (waiting < 2) {
                        try { Thread.sleep(20); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                    }
                }
                assertThat(waiting).as("both concurrent requests reached a database lock").isGreaterThanOrEqualTo(2);
            });
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }
    private void deselectAdded(long id) {
        long line = carts.addProduct(new AddCartProductRequest(id, 1)).items().stream()
                .filter(value -> value.productId() == id).findFirst().orElseThrow().id();
        carts.selectItem(line, new SelectCartItemRequest(false));
    }
    private void rejects(Runnable work, ErrorCode code) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(code));
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM uteexpress." + table, Integer.class); }
    private int stock(long id) { return jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, id); }
    private Map<String, List<Map<String, Object>>> databaseState() {
        Map<String, List<Map<String, Object>>> state = new TreeMap<>();
        for (String table : List.of("orders", "order_items", "order_status_history", "payments", "products", "carts", "cart_items")) {
            state.put(table, jdbc.queryForList("SELECT * FROM uteexpress." + table + " ORDER BY id"));
        }
        return state;
    }
    private CheckoutRequest request() { return request(List.of(new CheckoutRequest.Item(product, 2))); }
    private CheckoutRequest request(List<CheckoutRequest.Item> items) {
        return new CheckoutRequest(key, items, address, provider, "STANDARD", CheckoutRequest.PaymentMethod.COD, null);
    }
    private String body() {
        return "{\"checkoutKey\":\"" + key + "\",\"items\":[{\"productId\":" + product + ",\"quantity\":2}],\"addressId\":" + address
                + ",\"shippingProviderId\":" + provider + ",\"shippingServiceCode\":\"STANDARD\",\"paymentMethod\":\"COD\"}";
    }
    private long createUser() {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,'test-only-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, name + "@example.test", name + "@example.test", name, name);
    }
    private long createAddress(long owner) {
        return jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Receiver','0900000000','VN','District','DB address',true) RETURNING id", Long.class, owner);
    }
    private long createShop(long owner) {
        return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, owner, UUID.randomUUID().toString());
    }
    private long createProduct(long shopId, int price) {
        return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',?,10,'ACTIVE') RETURNING id", Long.class, shopId, category, price);
    }
    private static UteExpressPrincipal principal(long id) {
        return new UteExpressPrincipal(id, "buyer" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
    }
    private static void authenticate(long id) {
        var principal = principal(id);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    private static void authenticateAdmin(long id) {
        var admin = new UteExpressPrincipal(id, "admin" + id, null, 0,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()));
    }
    private void assertAvailabilityMutationWaiting(String relation) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("""
                    SELECT EXISTS (
                      SELECT 1 FROM pg_stat_activity
                       WHERE datname = current_database()
                         AND wait_event_type = 'Lock'
                         AND query ILIKE ?)
                    """, Boolean.class, "%" + relation + "%");
            if (Boolean.TRUE.equals(waiting)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Availability mutation did not wait on the " + relation + " row lock");
    }
}
