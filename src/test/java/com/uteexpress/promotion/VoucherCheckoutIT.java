package com.uteexpress.promotion;

import com.uteexpress.cart.dto.*;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import com.uteexpress.common.exception.*;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.BuyerOrderService;
import com.uteexpress.order.service.OrderPlacementService;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.order.service.VendorOrderService;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.VoucherService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class VoucherCheckoutIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired CheckoutQuoteService quotes;
    @Autowired OrderPlacementService placement;
    @Autowired BuyerOrderService buyerOrders;
    @Autowired VoucherService vouchers;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired VendorOrderService vendorOrders;
    @MockitoSpyBean CartService carts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    long buyer, other, vendor, address, otherAddress, shop, category, product, provider;
    String key;

    @BeforeEach void fixture() {
        for (String table : List.of("voucher_usages", "vouchers", "order_status_history", "payments", "order_items", "orders", "commission_policies")) {
            jdbc.update("DELETE FROM uteexpress." + table);
        }
        key = UUID.randomUUID().toString();
        buyer = createUser();
        other = createUser();
        vendor = createUser();
        address = address(buyer);
        otherAddress = address(other);
        shop = shop(vendor);
        category = category();
        product = product(shop, category);
        provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id",
                Long.class, "P" + key.replace("-", "").substring(0, 18).toUpperCase());
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',17000,true)", provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7.1234,'2020-01-01',?)", buyer);
        authenticate(buyer);
        carts.addProduct(new AddCartProductRequest(product, 2));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void vendorFormsCreateEditAndDisableVoucherUsedByRealCheckoutAndCancellation() throws Exception {
        mvc.perform(post("/vendor/vouchers").with(user(vendorPrincipal())).with(csrf())
                        .param("code", "SAVE").param("type", "PERCENTAGE").param("value", "10")
                        .param("minSubtotal", "0").param("startsAt", "2020-01-01T00:00")
                        .param("endsAt", "2100-01-01T00:00").param("totalLimit", "1")
                        .param("perUserLimit", "1").param("active", "true"))
                .andExpect(status().is3xxRedirection());
        long id = jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE code='SAVE'", Long.class);
        mvc.perform(post("/vendor/vouchers/" + id + "/update").with(user(vendorPrincipal())).with(csrf())
                        .param("version", "0").param("code", "SAVE").param("type", "PERCENTAGE").param("value", "20")
                        .param("minSubtotal", "0").param("startsAt", "2020-01-01T00:00")
                        .param("endsAt", "2100-01-01T00:00").param("totalLimit", "1")
                        .param("perUserLimit", "1").param("active", "true"))
                .andExpect(status().is3xxRedirection());
        authenticate(buyer);
        assertThat(quotes.quote(new QuoteRequest(address, provider, "STANDARD", "SAVE")).quote().totals().discountTotal())
                .isEqualByComparingTo("50000");
        var placed = placement.placeOrder(request("SAVE"));
        var originalHistory = jdbc.queryForMap("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?", id);
        var snapshots = historicalOrderFacts(placed.orderId());
        mvc.perform(post("/vendor/vouchers/" + id + "/disable").with(user(vendorPrincipal())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?", id)).isEqualTo(originalHistory);
        authenticate(buyer);
        carts.addProduct(new AddCartProductRequest(product, 2));
        var before = state();
        rejects(() -> quotes.quote(new QuoteRequest(address, provider, "STANDARD", "SAVE")), ErrorCode.Detail.VOUCHER_INACTIVE);
        rejects(() -> placement.placeOrder(command(address, product, 2, "disabled-new-key", "SAVE")), ErrorCode.Detail.VOUCHER_INACTIVE);
        assertThat(state()).isEqualTo(before);
        authenticateVendor();
        lifecycle.transition(new OrderTransitionCommand(placed.orderId(), OrderStatus.NEW,
                vendorOrders.detail(placed.orderId()).version(), OrderAction.CANCEL_NEW, "OUT_OF_STOCK"));
        var released = jdbc.queryForMap("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?", id);
        assertThat(released).containsEntry("status", "RELEASED");
        assertThat(released.get("released_at")).isNotNull();
        assertThat(originalUsageFacts(released)).isEqualTo(originalUsageFacts(originalHistory));
        assertThat(historicalOrderFacts(placed.orderId())).isEqualTo(snapshots);
    }

    @ParameterizedTest @ValueSource(strings = {"SHOP", "PLATFORM"})
    void quoteAndPlacementUsePersistedRulesAndSaveOneUsage(String scope) {
        long id = voucher(scope, 10, 1);
        var before = state();
        var quote = quotes.quote(new QuoteRequest(address, provider, "STANDARD", " save ")).quote();
        assertThat(quote.voucher()).isEqualTo(new VoucherApplication(id, "SAVE", VoucherScope.valueOf(scope), new BigDecimal("25000.00")));
        assertThat(quote.totals().subtotal()).isEqualByComparingTo("250000");
        assertThat(quote.totals().discountTotal()).isEqualByComparingTo("25000");
        assertThat(quote.totals().grandTotal()).isEqualByComparingTo("242000");
        assertThat(state()).isEqualTo(before);
        var result = placement.placeOrder(request("SAVE"));
        assertThat(result.grandTotal()).isEqualByComparingTo("242000");
        assertThat(result.status().name()).isEqualTo("NEW");
        var stored = order(result.orderId());
        assertThat(stored).containsEntry("voucher_id", id).containsEntry("voucher_code", "SAVE").containsEntry("voucher_scope", scope)
                .containsEntry("buyer_id", buyer).containsEntry("shipping_provider_id", provider).containsEntry("shipping_service_code", "STANDARD");
        assertThat((BigDecimal) stored.get("commission_amount")).isEqualByComparingTo("16028");
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.voucher_usages"))
                .containsEntry("voucher_id", id).containsEntry("user_id", buyer).containsEntry("order_id", result.orderId()).containsEntry("status", "REDEEMED");
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM uteexpress.voucher_usages", BigDecimal.class)).isEqualByComparingTo("25000");
        assertThat(jdbc.queryForMap("SELECT method,status,amount FROM uteexpress.payments"))
                .containsEntry("method", "COD").containsEntry("status", "UNPAID").containsEntry("amount", new BigDecimal("242000.00"));
        assertThat(count("voucher_usages")).isEqualTo(1);
        assertThat(stock()).isEqualTo(98);
    }

    @ParameterizedTest @ValueSource(strings = {"unknown", "malformed", "inactive", "future", "expired", "shop", "minimum"})
    void quoteAndPlacementRejectIneligibleVoucherWithoutWrites(String reason) {
        long id = voucher("SHOP", 10, 1);
        ErrorCode.Detail detail = switch (reason) {
            case "unknown" -> ErrorCode.Detail.VOUCHER_UNKNOWN;
            case "malformed" -> ErrorCode.Detail.VOUCHER_INVALID;
            case "inactive" -> { jdbc.update("UPDATE uteexpress.vouchers SET active=false WHERE id=?", id); yield ErrorCode.Detail.VOUCHER_INACTIVE; }
            case "future" -> { jdbc.update("UPDATE uteexpress.vouchers SET starts_at='2090-01-01' WHERE id=?", id); yield ErrorCode.Detail.VOUCHER_FUTURE; }
            case "expired" -> { jdbc.update("UPDATE uteexpress.vouchers SET ends_at='2021-01-01' WHERE id=?", id); yield ErrorCode.Detail.VOUCHER_EXPIRED; }
            case "shop" -> { jdbc.update("UPDATE uteexpress.vouchers SET shop_id=? WHERE id=?", shop(other), id); yield ErrorCode.Detail.VOUCHER_WRONG_SHOP; }
            case "minimum" -> { jdbc.update("UPDATE uteexpress.vouchers SET min_subtotal=250001 WHERE id=?", id); yield ErrorCode.Detail.VOUCHER_MINIMUM; }
            default -> throw new AssertionError(reason);
        };
        String code = reason.equals("unknown") ? "UNKNOWN" : reason.equals("malformed") ? "bad code" : "SAVE";
        var before = state();
        rejects(() -> quotes.quote(new QuoteRequest(address, provider, "STANDARD", code)), detail);
        rejects(() -> placement.placeOrder(request(code)), detail);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"inactive", "price", "quota"})
    void placementRevalidatesAfterEarlierQuote(String change) {
        long id = voucher("SHOP", 10, 1);
        quotes.quote(new QuoteRequest(address, provider, "STANDARD", "SAVE"));
        if (change.equals("inactive")) {
            jdbc.update("UPDATE uteexpress.vouchers SET active=false WHERE id=?", id);
            var before = state();
            rejects(() -> placement.placeOrder(request("SAVE")), ErrorCode.Detail.VOUCHER_INACTIVE);
            assertThat(state()).isEqualTo(before);
        } else if (change.equals("price")) {
            jdbc.update("UPDATE uteexpress.vouchers SET value=20 WHERE id=?", id);
            var result = placement.placeOrder(request("SAVE"));
            assertThat(result.grandTotal()).isEqualByComparingTo("217000");
            assertThat(buyerOrders.detail(result.orderId()).discountTotal()).isEqualByComparingTo("50000");
        } else {
            jdbc.update("UPDATE uteexpress.vouchers SET total_limit=1 WHERE id=?", id);
            authenticate(other);
            carts.addProduct(new AddCartProductRequest(product, 1));
            placement.placeOrder(command(otherAddress, product, 1, "other", "SAVE"));
            authenticate(buyer);
            var before = state();
            rejects(() -> placement.placeOrder(request("SAVE")), ErrorCode.Detail.VOUCHER_QUOTA);
            assertThat(state()).isEqualTo(before);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"global", "user"})
    void bothLimitsRejectAnotherUseAtQuoteAndPlacement(String limit) {
        voucher("PLATFORM", limit.equals("global") ? 1 : 10, 1);
        placement.placeOrder(request("SAVE"));
        if (limit.equals("global")) authenticate(other);
        carts.addProduct(new AddCartProductRequest(product, 2));
        Long selectedAddress = limit.equals("global") ? otherAddress : address;
        ErrorCode.Detail expected = limit.equals("global") ? ErrorCode.Detail.VOUCHER_QUOTA : ErrorCode.Detail.VOUCHER_USER_LIMIT;
        var before = state();
        rejects(() -> quotes.quote(new QuoteRequest(selectedAddress, provider, "STANDARD", "SAVE")), expected);
        rejects(() -> placement.placeOrder(command(selectedAddress, product, 2, "second", "SAVE")), expected);
        assertThat(state()).isEqualTo(before);
        assertThat(count("voucher_usages")).isEqualTo(1);
    }

    @Test void rollbackAfterUsageOrderPaymentAndCartWritesDoesNotConsumeQuotaAndCanRetry() {
        voucher("SHOP", 1, 1);
        var before = state();
        CartService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(carts);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("forced final cleanup failure"); })
                .when(target).removeCheckedOutItems(anyList());
        assertThatThrownBy(() -> placement.placeOrder(request("SAVE"))).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo(before);
        assertThat(count("voucher_usages")).isZero();
        doCallRealMethod().when(target).removeCheckedOutItems(anyList());
        assertThat(placement.placeOrder(request("SAVE")).replayed()).isFalse();
        assertThat(count("voucher_usages")).isEqualTo(1);
    }

    @Test void replayAndHistoricalDisplayIgnoreEditedInactiveVoucherAndNeverRewriteSnapshots() throws Exception {
        long id = voucher("SHOP", 1, 1);
        var first = placement.placeOrder(request(" save "));
        var original = order(first.orderId());
        jdbc.update("UPDATE uteexpress.vouchers SET code='CHANGED',scope='PLATFORM',shop_id=NULL,value=99,active=false WHERE id=?", id);
        var before = state();
        var replay = placement.placeOrder(request("SAVE"));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(state()).isEqualTo(before);
        assertThat(order(first.orderId())).isEqualTo(original);
        var detail = buyerOrders.detail(first.orderId());
        assertThat(detail.voucher().code()).isEqualTo("SAVE");
        assertThat(detail.voucher().scope()).isEqualTo(VoucherScope.SHOP);
        assertThat(detail.voucher().discountAmount()).isEqualByComparingTo("25000");
        clear();
        mvc.perform(get("/orders/{id}", first.orderId()).with(user(principal(buyer))).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("SAVE (SHOP)")));
        assertThat(count("voucher_usages")).isEqualTo(1);
    }

    @Test void changedVoucherWithSameKeyConflictsBeforeAnyMutableCheckoutRead() {
        voucher("SHOP", 10, 1);
        placement.placeOrder(request("SAVE"));
        var before = state();
        for (String changed : List.of("OTHER", "")) {
            assertThatThrownBy(() -> placement.placeOrder(request(changed))).isInstanceOfSatisfying(ApplicationException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        }
        assertThat(state()).isEqualTo(before);
    }

    @Test void noVoucherAndLegacyOrderRemainReadableWithoutBackfill() throws Exception {
        var result = placement.placeOrder(request(null));
        assertThat(result.grandTotal()).isEqualByComparingTo("267000");
        assertThat(order(result.orderId())).containsEntry("voucher_id", null).containsEntry("voucher_code", null).containsEntry("voucher_scope", null);
        assertThat(buyerOrders.detail(result.orderId()).voucher()).isNull();
        assertThat(count("voucher_usages")).isZero();
        // Simulate older ORD/PAY rows with absent historical shipping/payment facts too.
        jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?", result.orderId());
        jdbc.update("UPDATE uteexpress.orders SET shipping_provider_id=NULL,shipping_service_code=NULL WHERE id=?", result.orderId());
        var before = state();
        assertThat(placement.placeOrder(request(" ")).replayed()).isTrue();
        clear();
        mvc.perform(get("/orders/{id}", result.orderId()).with(user(principal(buyer)))).andExpect(status().isOk());
        assertThat(state()).isEqualTo(before);
    }

    @Test void fixedDiscountLargerThanSubtotalLeavesOnlyAuthoritativeShippingPayable() {
        long id = voucher("PLATFORM", 10, 1);
        jdbc.update("UPDATE uteexpress.vouchers SET type='FIXED',value=999999 WHERE id=?", id);
        var result = placement.placeOrder(request("SAVE"));
        assertThat(result.grandTotal()).isEqualByComparingTo("17000");
        assertThat((BigDecimal) order(result.orderId()).get("discount_total")).isEqualByComparingTo("250000");
        assertThat((BigDecimal) order(result.orderId()).get("commission_amount")).isZero();
    }

    @Test void percentageMaximumAndHalfUpRoundingArePersisted() {
        long id = voucher("SHOP", 10, 1);
        jdbc.update("UPDATE uteexpress.vouchers SET value=12.5,max_discount=99999,min_subtotal=0 WHERE id=?", id);
        jdbc.update("UPDATE uteexpress.products SET price=101 WHERE id=?", product);
        var result = placement.placeOrder(request("SAVE"));
        assertThat((BigDecimal) order(result.orderId()).get("discount_total")).isEqualByComparingTo("25");
        assertThat(result.grandTotal()).isEqualByComparingTo("17177");
    }

    @Test void concurrentBuyersOnIndependentProductsShopsCategoriesCompeteForLastUsage() throws Exception {
        long id = voucher("PLATFORM", 1, 1);
        // Disjoint preceding locks are essential: this test must contend on the voucher itself.
        long otherProduct = product(shop(other), category());
        authenticate(other);
        carts.addProduct(new AddCartProductRequest(otherProduct, 2));
        var results = concurrent(id, buyer, request("SAVE"), other, command(otherAddress, otherProduct, 2, "other", "SAVE"));
        assertThat(results.stream().filter(PlaceOrderResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(ApplicationException.class::isInstance)).singleElement()
                .satisfies(error -> assertThat(((ApplicationException) error).detail()).isEqualTo(ErrorCode.Detail.VOUCHER_QUOTA));
        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("voucher_usages")).isEqualTo(1);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(stock) FROM uteexpress.products WHERE id IN (?,?)", Integer.class, product, otherProduct)).isEqualTo(198);
    }

    @Test void concurrentSameBuyerReplayConsumesOnce() throws Exception {
        long id = voucher("SHOP", 1, 1);
        var results = concurrent(id, buyer, request("SAVE"), buyer, request(" save "));
        assertThat(results).allMatch(PlaceOrderResult.class::isInstance);
        var receipts = results.stream().map(PlaceOrderResult.class::cast).toList();
        assertThat(receipts).extracting(PlaceOrderResult::orderId).containsOnly(receipts.getFirst().orderId());
        assertThat(receipts.stream().filter(PlaceOrderResult::replayed)).hasSize(1);
        assertThat(count("voucher_usages")).isEqualTo(1);
        assertThat(stock()).isEqualTo(98);
    }

    @Test void concurrentDifferentKeysForOneBuyerCannotExceedPerUserLimit() throws Exception {
        long id = voucher("SHOP", 10, 1);
        var results = concurrent(id, buyer, request("SAVE"), buyer, command(address, product, 2, "second", "SAVE"));
        assertThat(results.stream().filter(PlaceOrderResult.class::isInstance)).hasSize(1);
        assertThat(results.stream().filter(ApplicationException.class::isInstance)).hasSize(1);
        assertThat(count("voucher_usages")).isEqualTo(1);
        assertThat(count("orders")).isEqualTo(1);
    }

    @Test void htmlVoucherQuoteCarriesCanonicalSelectionThroughPlacementAndShowsActionableErrors() throws Exception {
        voucher("SHOP", 10, 1);
        clear();
        var preview = mvc.perform(post("/user/checkout/view").with(user(principal(buyer))).with(csrf())
                .param("addressId", Long.toString(address)).param("shippingProviderId", Long.toString(provider))
                .param("shippingServiceCode", "STANDARD").param("voucherCode", " save "))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"voucherCode\" value=\"SAVE\"")))
                .andExpect(content().string(containsString("25.000"))).andReturn();
        String checkoutKey = (String) preview.getModelAndView().getModel().get("placeOrderKey");
        var submit = mvc.perform(post("/user/checkout/view/place-order").with(user(principal(buyer))).with(csrf())
                .param("checkoutKey", checkoutKey).param("addressId", Long.toString(address))
                .param("shippingProviderId", Long.toString(provider)).param("shippingServiceCode", "STANDARD")
                .param("paymentMethod", "COD").param("voucherCode", "SAVE")
                .param("items[0].productId", Long.toString(product)).param("items[0].quantity", "2"))
                .andExpect(redirectedUrl("/user/checkout/view")).andExpect(flash().attributeExists("placedOrder")).andReturn();
        assertThat(((PlaceOrderResult) submit.getFlashMap().get("placedOrder")).grandTotal()).isEqualByComparingTo("242000");
        authenticate(buyer);
        carts.addProduct(new AddCartProductRequest(product, 2));
        clear();
        mvc.perform(post("/user/checkout/view").with(user(principal(buyer))).with(csrf())
                .param("addressId", Long.toString(address)).param("shippingProviderId", Long.toString(provider))
                .param("shippingServiceCode", "STANDARD").param("voucherCode", "SAVE"))
                .andExpect(redirectedUrl("/user/checkout/view"))
                .andExpect(flash().attribute("errorMessage", ErrorCode.Detail.VOUCHER_USER_LIMIT.message()));
    }

    @Test void jsonApiReturnsServerVoucherFactsAndRejectsForgedDiscountBuyerAndShop() throws Exception {
        voucher("SHOP", 10, 1);
        clear();
        String quote = "{\"addressId\":" + address + ",\"shippingProviderId\":" + provider + ",\"shippingServiceCode\":\"STANDARD\",\"voucherCode\":\"save\"}";
        mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf()).contentType("application/json").content(quote))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quote.voucher.code").value("SAVE"))
                .andExpect(jsonPath("$.quote.totals.discountTotal").value(25000)).andExpect(jsonPath("$.quote.totals.grandTotal").value(242000));
        var before = state();
        for (String field : List.of("discountAmount", "discountTotal", "voucherId", "buyerId", "userId", "shopId", "totalLimit")) {
            String forged = quote.substring(0, quote.length() - 1) + ",\"" + field + "\":1}";
            mvc.perform(post("/user/checkout/quote").with(user(principal(buyer))).with(csrf()).contentType("application/json").content(forged))
                    .andExpect(status().isBadRequest());
            String place = placeBody().replaceFirst("}$", ",\"" + field + "\":1}");
            mvc.perform(post("/user/checkout/place-order").with(user(principal(buyer))).with(csrf()).contentType("application/json").content(place))
                    .andExpect(status().isBadRequest());
        }
        assertThat(state()).isEqualTo(before);
        mvc.perform(post("/user/checkout/place-order").with(user(principal(buyer))).with(csrf()).contentType("application/json").content(placeBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.grandTotal").value(242000));
        assertThat(jdbc.queryForObject("SELECT user_id FROM uteexpress.voucher_usages", Long.class)).isEqualTo(buyer);
    }

    @Test void voucherApisRetainAuthenticationAndCsrfAndDoNotExposeForeignOrders() throws Exception {
        voucher("SHOP", 10, 1);
        var result = placement.placeOrder(request("SAVE"));
        var before = state();
        clear();
        mvc.perform(post("/user/checkout/place-order").with(user(principal(buyer))).contentType("application/json").content(placeBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/user/checkout/place-order").with(csrf()).contentType("application/json").content(placeBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/{id}", result.orderId()).with(user(principal(other))).accept("application/json"))
                .andExpect(status().isNotFound());
        assertThat(state()).isEqualTo(before);
    }

    @Test void recordingUsageRequiresCheckedApplicationInSameTransaction() {
        var fake = new VoucherApplication(1L, "SAVE", VoucherScope.SHOP, new BigDecimal("1"));
        assertThatThrownBy(() -> vouchers.recordUsage(fake, 1L)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThatThrownBy(() -> vouchers.recordUsage(fake, 1L)).isInstanceOfSatisfying(ApplicationException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
                tx.setRollbackOnly();
        });
        assertThat(count("voucher_usages")).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"scope", "type", "percent", "fraction", "window", "limit", "code", "minimum"})
    void postgresRejectsInvalidPersistedDefinitions(String rule) {
        long id = voucher("SHOP", 10, 1);
        String mutation = switch (rule) {
            case "scope" -> "scope='PLATFORM'";
            case "type" -> "type='UNKNOWN'";
            case "percent" -> "value=101";
            case "fraction" -> "type='FIXED',value=1.5";
            case "window" -> "ends_at=starts_at";
            case "limit" -> "per_user_limit=0";
            case "code" -> "code='lowercase'";
            case "minimum" -> "min_subtotal=1.5";
            default -> throw new AssertionError(rule);
        };
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.vouchers SET " + mutation + " WHERE id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void postgresEnforcesCanonicalUniqueCodeUniqueUsageAndOrderBuyerFk() {
        long id = voucher("SHOP", 10, 1);
        assertThatThrownBy(() -> voucher("SHOP", 10, 1)).isInstanceOf(DataIntegrityViolationException.class);
        var result = placement.placeOrder(request("SAVE"));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.voucher_usages(voucher_id,user_id,order_id,status,discount_amount) VALUES (?,?,?,'REDEEMED',25000)", id, buyer, result.orderId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.voucher_usages SET user_id=? WHERE order_id=?", other, result.orderId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"NEW", "CONFIRMED"})
    void preDeliveryCancellationReleasesOnceAndPreservesHistoricalFacts(String status) throws Exception {
        voucher("SHOP", 1, 1);
        var receipt = placement.placeOrder(request("SAVE"));
        var redeemed = usage(receipt.orderId());
        assertThat(redeemed).containsEntry("status", "REDEEMED").containsEntry("released_at", null)
                .containsEntry("user_id", buyer);
        authenticateVendor();
        if (status.equals("CONFIRMED")) lifecycle.transition(new OrderTransitionCommand(receipt.orderId(), OrderStatus.NEW, 0L, OrderAction.CONFIRM, null));
        var snapshots = historicalOrderFacts(receipt.orderId());
        long version = status.equals("NEW") ? 0 : 1;
        cancel(receipt.orderId(), OrderStatus.valueOf(status), version);
        var released = usage(receipt.orderId());
        assertThat(released).containsEntry("status", "RELEASED");
        assertThat(released.get("released_at")).isNotNull();
        assertThat(originalUsageFacts(released)).isEqualTo(originalUsageFacts(redeemed));
        assertThat(historicalOrderFacts(receipt.orderId())).isEqualTo(snapshots);
        assertThat(stock()).isEqualTo(100);
        assertConflict(() -> cancel(receipt.orderId(), OrderStatus.valueOf(status), version));
        assertThat(usage(receipt.orderId())).isEqualTo(released);
        assertThat(vendorOrders.detail(receipt.orderId()).facts().voucher().code()).isEqualTo("SAVE");
        clear();
        mvc.perform(get("/vendor/orders/{id}", receipt.orderId()).with(user(vendorPrincipal())).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("SAVE (SHOP)")));
        authenticate(buyer);
        assertThat(buyerOrders.detail(receipt.orderId()).voucher().discountAmount()).isEqualByComparingTo("25000");
        carts.addProduct(new AddCartProductRequest(product, 2));
        assertThat(quotes.quote(new QuoteRequest(address, provider, "STANDARD", "SAVE")).quote().voucher()).isNotNull();
    }

    @ParameterizedTest @ValueSource(strings = {"global", "user"})
    void cancellationRestoresGlobalAndPerUserQuotaWithoutDeletingHistory(String limit) {
        voucher("PLATFORM", limit.equals("global") ? 1 : 10, 1);
        var first = placement.placeOrder(request("SAVE"));
        long actor = limit.equals("global") ? other : buyer;
        long selectedAddress = limit.equals("global") ? otherAddress : address;
        authenticate(actor);
        carts.addProduct(new AddCartProductRequest(product, 2));
        var next = command(selectedAddress, product, 2, "next", "SAVE");
        rejects(() -> placement.placeOrder(next), limit.equals("global") ? ErrorCode.Detail.VOUCHER_QUOTA : ErrorCode.Detail.VOUCHER_USER_LIMIT);
        authenticateVendor();
        cancel(first.orderId(), OrderStatus.NEW, 0);
        authenticate(actor);
        var second = placement.placeOrder(next);
        assertThat(count("voucher_usages")).isEqualTo(2);
        assertThat(usage(first.orderId())).containsEntry("status", "RELEASED");
        assertThat(usage(second.orderId())).containsEntry("status", "REDEEMED").containsEntry("released_at", null).containsEntry("user_id", actor);
        assertThat(activeUses()).isEqualTo(1);
    }

    @Test void historyFailureAfterReleaseRollsBackQuotaStockStatusAndHistory() {
        voucher("SHOP", 1, 1);
        var receipt = placement.placeOrder(request("SAVE"));
        authenticateVendor();
        var before = state();
        jdbc.execute("ALTER TABLE uteexpress.order_status_history ADD CONSTRAINT promo_test_cancel_history CHECK (to_status <> 'CANCELLED')");
        try {
            // Order.saveAndFlush flushes RELEASED before this final history insert fails.
            assertThatThrownBy(() -> cancel(receipt.orderId(), OrderStatus.NEW, 0)).isInstanceOf(DataIntegrityViolationException.class);
        } finally { jdbc.execute("ALTER TABLE uteexpress.order_status_history DROP CONSTRAINT promo_test_cancel_history"); }
        assertThat(state()).isEqualTo(before);
        assertThat(usage(receipt.orderId())).containsEntry("status", "REDEEMED").containsEntry("released_at", null);
        assertThat(activeUses()).isEqualTo(1);
        authenticate(buyer);
        carts.addProduct(new AddCartProductRequest(product, 2));
        rejects(() -> placement.placeOrder(command(address, product, 2, "still-full", "SAVE")), ErrorCode.Detail.VOUCHER_QUOTA);
    }

    @Test void outerRollbackDoesNotExposeReleasedQuota() {
        voucher("SHOP", 1, 1);
        var receipt = placement.placeOrder(request("SAVE"));
        authenticateVendor();
        var before = state();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            cancel(receipt.orderId(), OrderStatus.NEW, 0);
            assertThat(usage(receipt.orderId())).containsEntry("status", "RELEASED");
            tx.setRollbackOnly();
        });
        assertThat(state()).isEqualTo(before);
        assertThat(activeUses()).isEqualTo(1);
    }

    @Test void cancellationReleasesEditedInactiveExpiredVoucherWithoutEligibilityChecks() {
        long id = voucher("SHOP", 1, 1);
        var receipt = placement.placeOrder(request("SAVE"));
        var snapshots = historicalOrderFacts(receipt.orderId());
        jdbc.update("UPDATE uteexpress.vouchers SET code='EDITED',scope='PLATFORM',shop_id=NULL,value=99,active=false,ends_at='2021-01-01',min_subtotal=999999,total_limit=2,per_user_limit=2 WHERE id=?", id);
        authenticateVendor();
        cancel(receipt.orderId(), OrderStatus.NEW, 0);
        assertThat(usage(receipt.orderId())).containsEntry("status", "RELEASED");
        assertThat(historicalOrderFacts(receipt.orderId())).isEqualTo(snapshots);
    }

    @Test void checkoutReplayAfterCancellationNeverReconsumesQuotaOrRewritesSnapshots() {
        long id = voucher("SHOP", 1, 1);
        var command = request("SAVE");
        var first = placement.placeOrder(command);
        authenticateVendor();
        cancel(first.orderId(), OrderStatus.NEW, 0);
        jdbc.update("UPDATE uteexpress.vouchers SET code='EDITED',active=false WHERE id=?", id);
        authenticate(buyer);
        var before = state();
        var replay = placement.placeOrder(command);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(replay.status()).isEqualTo(OrderStatus.CANCELLED);
        assertConflict(() -> placement.placeOrder(request("OTHER")));
        assertThat(state()).isEqualTo(before);
        assertThat(count("voucher_usages")).isEqualTo(1);
        assertThat(activeUses()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"current", "legacy"})
    void noVoucherCancellationPreservesLegacyBehaviorWithoutInventedUsage(String kind) {
        var receipt = placement.placeOrder(request(null));
        if (kind.equals("legacy")) {
            jdbc.update("UPDATE uteexpress.orders SET shipping_provider_id=NULL,shipping_service_code=NULL WHERE id=?", receipt.orderId());
            jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?", receipt.orderId());
        }
        var snapshots = historicalOrderFacts(receipt.orderId());
        authenticateVendor();
        cancel(receipt.orderId(), OrderStatus.NEW, 0);
        assertThat(order(receipt.orderId())).containsEntry("status", "CANCELLED");
        assertThat(stock()).isEqualTo(100);
        assertThat(count("voucher_usages")).isZero();
        assertThat(historicalOrderFacts(receipt.orderId())).isEqualTo(snapshots);
    }

    @Test void nonCancellationAndDeniedPostDeliveryReturnNeverReleaseUsage() {
        voucher("SHOP", 1, 1);
        var receipt = placement.placeOrder(request("SAVE"));
        var redeemed = usage(receipt.orderId());
        authenticateVendor();
        lifecycle.transition(new OrderTransitionCommand(receipt.orderId(), OrderStatus.NEW, 0L, OrderAction.CONFIRM, null));
        lifecycle.markReady(new OrderReadyCommand(receipt.orderId(), 1L));
        assertThat(usage(receipt.orderId())).isEqualTo(redeemed);
        // Persisted delivered fixture exercises existing guards; no return implementation is introduced.
        jdbc.update("UPDATE uteexpress.orders SET status='DELIVERED',delivered_at=CURRENT_TIMESTAMP WHERE id=?", receipt.orderId());
        var before = state();
        assertConflict(() -> lifecycle.transition(new OrderTransitionCommand(receipt.orderId(), OrderStatus.DELIVERED, 2L, OrderAction.REQUEST_RETURN, null)));
        assertConflict(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> vouchers.releaseForCancellation(receipt.orderId())));
        assertThat(state()).isEqualTo(before);
        assertThat(activeUses()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "discount"})
    void corruptVoucherUsageFailsCancellationAndRollsBackInventory(String corruption) {
        long id = voucher("SHOP", 1, 1);
        var receipt = placement.placeOrder(request(corruption.equals("missing") ? null : "SAVE"));
        if (corruption.equals("missing")) {
            // A voucher snapshot without its expected usage is corruption, not a legacy no-voucher Order.
            jdbc.update("UPDATE uteexpress.orders SET voucher_id=?,voucher_code='SAVE',voucher_scope='SHOP' WHERE id=?", id, receipt.orderId());
        } else jdbc.update("UPDATE uteexpress.voucher_usages SET discount_amount=1 WHERE order_id=?", receipt.orderId());
        authenticateVendor();
        var before = state();
        assertConflict(() -> cancel(receipt.orderId(), OrderStatus.NEW, 0));
        assertThat(state()).isEqualTo(before);
    }

    @Test void releaseRequiresExistingLifecycleTransaction() {
        assertThatThrownBy(() -> vouchers.releaseForCancellation(1L)).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"active-with-time", "released-without-time", "unknown"})
    void postgresEnforcesUsageStateAndReleaseTimestampConsistency(String combination) {
        voucher("SHOP", 1, 1);
        var receipt = placement.placeOrder(request("SAVE"));
        String mutation = switch (combination) {
            case "active-with-time" -> "released_at=CURRENT_TIMESTAMP";
            case "released-without-time" -> "status='RELEASED'";
            case "unknown" -> "status='UNKNOWN'";
            default -> throw new AssertionError(combination);
        };
        var before = usage(receipt.orderId());
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.voucher_usages SET " + mutation + " WHERE order_id=?", receipt.orderId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(usage(receipt.orderId())).isEqualTo(before);
    }

    @Test void concurrentCancellationHasOneLifecycleEffectAndOneRelease() throws Exception {
        voucher("SHOP", 1, 1);
        var receipt = placement.placeOrder(request("SAVE"));
        var redeemed = usage(receipt.orderId());
        var executor = Executors.newFixedThreadPool(2);
        var futures = new ArrayList<Future<Object>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, receipt.orderId());
                for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> cancellationWorker(receipt.orderId())));
                awaitLockWaits(2, ""); // First worker waits on Order; second waits on the vendor account.
            });
            assertThat(List.of(futures.get(0).get(25, TimeUnit.SECONDS), futures.get(1).get(25, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", ErrorCode.CONFLICT);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(25, TimeUnit.SECONDS)).isTrue(); }
        assertThat(count("voucher_usages")).isEqualTo(1);
        var released = usage(receipt.orderId());
        assertThat(released).containsEntry("status", "RELEASED").containsEntry("version", 1L);
        assertThat(released.get("released_at")).isNotNull();
        assertThat(originalUsageFacts(released)).isEqualTo(originalUsageFacts(redeemed));
        assertThat(order(receipt.orderId())).containsEntry("status", "CANCELLED").containsEntry("version", 1L);
        assertThat(count("order_status_history")).isEqualTo(2);
        assertThat(stock()).isEqualTo(100);
        assertThat(activeUses()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"shared-products", "independent-products"})
    void placementAndCancellationUseCompatiblePostgresLocksAndNeverExceedQuota(String resources) throws Exception {
        long id = voucher("PLATFORM", 1, 1);
        var first = placement.placeOrder(request("SAVE"));
        long nextProduct = resources.equals("shared-products") ? product : product(shop(other), category());
        authenticate(other);
        carts.addProduct(new AddCartProductRequest(nextProduct, 2));
        var next = command(otherAddress, nextProduct, 2, "competing", "SAVE");
        var executor = Executors.newFixedThreadPool(2);
        var futures = new ArrayList<Future<Object>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE id=? FOR UPDATE", Long.class, id);
                futures.add(executor.submit(() -> cancellationWorker(first.orderId())));
                awaitLockWaits(1, "vouchers"); // Cancellation has already retained Product and Shop locks.
                futures.add(executor.submit(() -> placementWorker(other, next)));
                if (resources.equals("shared-products")) awaitLockWaits(1, "products");
                else awaitLockWaits(2, "vouchers");
            });
            assertThat(futures.get(0).get(25, TimeUnit.SECONDS)).isEqualTo("OK");
            var result = futures.get(1).get(25, TimeUnit.SECONDS);
            assertThat(activeUses()).isLessThanOrEqualTo(1);
            if (result instanceof ApplicationException error) {
                assertThat(resources).isEqualTo("independent-products");
                assertThat(error.detail()).isEqualTo(ErrorCode.Detail.VOUCHER_QUOTA);
                authenticate(other);
                result = placement.placeOrder(next); // The earlier quota failure had no successful key reservation.
            }
            assertThat(result).isInstanceOf(PlaceOrderResult.class);
            assertThat(usage(((PlaceOrderResult) result).orderId())).containsEntry("status", "REDEEMED").containsEntry("user_id", other);
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(25, TimeUnit.SECONDS)).isTrue(); }
        assertThat(usage(first.orderId())).containsEntry("status", "RELEASED");
        assertThat(count("voucher_usages")).isEqualTo(2);
        assertThat(activeUses()).isEqualTo(1);
    }

    private Object cancellationWorker(long orderId) {
        authenticateVendor();
        var result = timedWorker(() -> { cancel(orderId, OrderStatus.NEW, 0); return "OK"; });
        return result instanceof ApplicationException error ? error.errorCode() : result;
    }
    private Object placementWorker(long actor, CheckoutRequest command) {
        authenticate(actor);
        return timedWorker(() -> placement.placeOrder(command));
    }
    private Object timedWorker(java.util.function.Supplier<Object> work) {
        try {
            return new TransactionTemplate(transactions).execute(tx -> {
                jdbc.execute("SET LOCAL lock_timeout='15s'");
                jdbc.execute("SET LOCAL statement_timeout='20s'");
                return work.get();
            });
        } catch (ApplicationException error) { return error; }
        finally { clear(); }
    }
    private void awaitLockWaits(int expected, String relation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int waiting = 0;
        while (waiting < expected && System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE ?", Integer.class, "%" + relation + "%");
            if (waiting < expected) {
                try { Thread.sleep(20); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            }
        }
        assertThat(waiting).as("PostgreSQL waiters on " + relation).isEqualTo(expected);
    }
    private void cancel(long orderId, OrderStatus status, long version) {
        lifecycle.transition(new OrderTransitionCommand(orderId, status, version,
                status == OrderStatus.NEW ? OrderAction.CANCEL_NEW : OrderAction.CANCEL_CONFIRMED, "UNABLE_TO_FULFILL"));
    }
    private Map<String, Object> usage(long orderId) { return jdbc.queryForMap("SELECT * FROM uteexpress.voucher_usages WHERE order_id=?", orderId); }
    private int activeUses() { return jdbc.queryForObject("SELECT count(*) FROM uteexpress.voucher_usages WHERE status='REDEEMED'", Integer.class); }
    private Map<String, Object> originalUsageFacts(Map<String, Object> row) {
        var original = new TreeMap<>(row);
        for (String field : List.of("status", "released_at", "version")) original.remove(field);
        return original;
    }
    private Map<String, Object> historicalOrderFacts(long orderId) {
        var facts = new TreeMap<>(order(orderId));
        for (String field : List.of("status", "updated_at", "cancelled_at", "cancellation_reason", "inventory_released_at", "version")) facts.remove(field);
        return facts;
    }
    private void assertConflict(Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class, error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
    private UteExpressPrincipal vendorPrincipal() {
        return new UteExpressPrincipal(vendor, "vendor" + vendor, null, 0, List.of(new SimpleGrantedAuthority("ROLE_VENDOR")), true);
    }
    private void authenticateVendor() {
        var user = vendorPrincipal();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    private List<Object> concurrent(long voucherId, long firstBuyer, CheckoutRequest first, long secondBuyer, CheckoutRequest second) throws Exception {
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<Future<Object>>();
            for (int i = 0; i < 2; i++) {
                long actor = i == 0 ? firstBuyer : secondBuyer;
                var command = i == 0 ? first : second;
                futures.add(executor.submit(() -> {
                    authenticate(actor);
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timed out");
                        return placement.placeOrder(command);
                    } catch (ApplicationException failure) { return failure; }
                    finally { SecurityContextHolder.clearContext(); }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE id=? FOR UPDATE", Long.class, voucherId);
                start.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                int waiting = 0;
                while (waiting < 2 && System.nanoTime() < deadline) {
                    // PostgreSQL caches statistics in a transaction: refresh each observation.
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'"
                            + (firstBuyer != secondBuyer ? " AND query ILIKE '%vouchers%'" : ""), Integer.class);
                    if (waiting < 2) {
                        try { Thread.sleep(20); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
                    }
                }
                assertThat(waiting).as(firstBuyer != secondBuyer
                        ? "independent checkouts both reached the voucher row lock"
                        : "same-buyer requests overlap on buyer/voucher locks").isEqualTo(2);
            });
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }
    private long voucher(String scope, int total, int perUser) {
        return jdbc.queryForObject("INSERT INTO uteexpress.vouchers(code,scope,shop_id,type,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,active,created_by) VALUES ('SAVE',?,?,'PERCENTAGE',10,100,'2020-01-01','2100-01-01',?,?,true,?) RETURNING id",
                Long.class, scope, scope.equals("SHOP") ? shop : null, total, perUser, buyer);
    }
    private long createUser() {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,'test-only-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, name + "@example.test", name + "@example.test", name, name);
    }
    private long address(long owner) {
        return jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Receiver','0900000000','VN','District','Address',true) RETURNING id", Long.class, owner);
    }
    private long shop(long owner) {
        return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, owner, UUID.randomUUID().toString());
    }
    private long category() {
        return jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id", Long.class, UUID.randomUUID().toString());
    }
    private long product(long targetShop, long targetCategory) {
        return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',125000,100,'ACTIVE') RETURNING id", Long.class, targetShop, targetCategory);
    }
    private CheckoutRequest request(String voucher) { return command(address, product, 2, key, voucher); }
    private CheckoutRequest command(long chosenAddress, long chosenProduct, int quantity, String chosenKey, String voucher) {
        return new CheckoutRequest(chosenKey, List.of(new CheckoutRequest.Item(chosenProduct, quantity)), chosenAddress, provider,
                "STANDARD", CheckoutRequest.PaymentMethod.COD, voucher);
    }
    private String placeBody() {
        return "{\"checkoutKey\":\"" + key + "\",\"items\":[{\"productId\":" + product + ",\"quantity\":2}],\"addressId\":" + address
                + ",\"shippingProviderId\":" + provider + ",\"shippingServiceCode\":\"STANDARD\",\"paymentMethod\":\"COD\",\"voucherCode\":\"SAVE\"}";
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM uteexpress." + table, Integer.class); }
    private int stock() { return jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, product); }
    private Map<String, Object> order(long id) { return jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", id); }
    private Map<String, List<Map<String, Object>>> state() {
        var result = new TreeMap<String, List<Map<String, Object>>>();
        for (String table : List.of("vouchers", "voucher_usages", "orders", "order_items", "order_status_history", "payments", "products", "carts", "cart_items")) {
            result.put(table, jdbc.queryForList("SELECT * FROM uteexpress." + table + " ORDER BY id"));
        }
        return result;
    }
    private void rejects(Runnable work, ErrorCode.Detail expected) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class, error -> assertThat(error.detail()).isEqualTo(expected));
    }
    private static UteExpressPrincipal principal(long id) {
        return new UteExpressPrincipal(id, "buyer" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_USER")), true);
    }
    private static void authenticate(long id) {
        var user = principal(id);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }
}
