package com.uteexpress.order;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.order.service.*;
import com.uteexpress.payment.service.PaymentService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.jwt.JwtTokenService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc @Testcontainers
class VendorOrderIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    static final Instant CREATED = Instant.parse("2026-10-03T04:05:06Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired VendorOrderService reads;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired OrderPlacementService placement;
    @Autowired CartService carts;
    @Autowired PaymentService payments;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory emf;
    @Autowired JwtTokenService tokens;
    @Autowired com.uteexpress.governance.service.ShopModerationService moderation;
    TransactionTemplate transactions;
    long vendor, other, buyer, shop, otherShop, product, secondProduct;
    Order owned, foreign;

    @BeforeEach void setup() {
        for (var table : List.of("order_status_history", "payments", "order_items", "orders")) {
            jdbc.update("DELETE FROM uteexpress." + table);
        }
        transactions = new TransactionTemplate(transactionManager);
        vendor = createUser(); other = createUser(); buyer = createUser();
        shop = createShop(vendor); otherShop = createShop(other);
        product = createProduct(shop, 8); secondProduct = createProduct(shop, 9);
        owned = createOrder(shop, "OWN", CREATED);
        foreign = createOrder(otherShop, "FOREIGN", CREATED.plusSeconds(10));
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void listAndDetailAreScopedToPersistedShopAndNeverBrowserIdentity() throws Exception {
        mvc.perform(get("/vendor/orders").with(user(principal(vendor, "VENDOR")))
                        .param("shopId", "" + otherShop).param("vendorId", "" + other).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(owned.getId()))
                .andExpect(jsonPath("$.content[0].checkoutKey").doesNotExist());
        mvc.perform(get("/vendor/orders/{id}", owned.getId()).with(user(principal(vendor, "VENDOR")))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.facts.items[1].quantity").value(2))
                .andExpect(jsonPath("$.facts.timeline.length()").value(1))
                .andExpect(jsonPath("$.facts.payments[0].status").value("UNPAID"))
                .andExpect(jsonPath("$.facts.requestHash").doesNotExist());
    }

    @ParameterizedTest @ValueSource(strings = {"application/json", "text/html"})
    void missingForeignAndInvalidSelectionsDoNotLeakOrder(String type) throws Exception {
        for (long id : List.of(foreign.getId(), Long.MAX_VALUE, 0L, -1L)) {
            mvc.perform(get("/vendor/orders/{id}", id).with(user(principal(vendor, "VENDOR"))).accept(type))
                    .andExpect(status().isNotFound()).andExpect(content().string(not(containsString("FOREIGN"))));
        }
    }

    @Test void paginationUsesDatabaseLimitsAndDeterministicOrdering() {
        var tie = createOrder(shop, "TIE", CREATED);
        var newest = createOrder(shop, "NEWEST", CREATED.plusSeconds(1));
        createOrder(shop, "OLDER", CREATED.minusSeconds(1));
        authenticate(vendor);
        var statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var first = reads.list(0, 2);
        // One account + one shop + only two orders; no items/history/payment entities loaded.
        assertThat(statistics.getEntityLoadCount()).isEqualTo(4);
        assertThat(first.content()).extracting(BuyerOrderSummary::id).containsExactly(newest.getId(), tie.getId());
        assertThat(first.totalElements()).isEqualTo(4);
        assertThat(first.hasNext()).isTrue();
        assertThat(reads.list(1, 2).content()).extracting(BuyerOrderSummary::orderCode).containsExactly("OWN", "OLDER");
        assertThat(reads.list(99, 2).content()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=2147483647", "page=abc"})
    void invalidPagesReturnControlledErrors(String query) throws Exception {
        for (var type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            mvc.perform(get("/vendor/orders?" + query).with(user(principal(vendor, "VENDOR"))).accept(type))
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "SHIPPER", "ADMIN", "MANAGER"})
    void nonVendorCannotReadOrMutateEvenWithKnownOrderAndCsrf(String role) throws Exception {
        var before = state();
        mvc.perform(get("/vendor/orders").with(user(principal(vendor, role)))).andExpect(status().isForbidden());
        mvc.perform(get("/vendor/orders/{id}", owned.getId()).with(user(principal(vendor, role))))
                .andExpect(status().isForbidden());
        for (String operation : List.of("confirm", "ready", "cancel")) {
            mvc.perform(post("/vendor/orders/{id}/{operation}", owned.getId(), operation)
                            .with(user(principal(vendor, role))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body(0))).andExpect(status().isForbidden());
        }
        assertThat(state()).isEqualTo(before);
    }

    @Test void guestAndFakeNumericPrincipalCannotResolveVendorIdentity() throws Exception {
        mvc.perform(get("/vendor/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/vendor/orders/{id}/confirm", owned.getId()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(0))).andExpect(status().isUnauthorized());
        mvc.perform(get("/vendor/orders").with(user("" + vendor).roles("VENDOR")))
                .andExpect(status().isUnauthorized());
    }

    @Test void actualJwtRestoresVendorAndRechecksAccountAndPersistedRoles() throws Exception {
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='VENDOR'", vendor);
        var cookie = new Cookie("UTEEXPRESS_AUTH", tokens.issue(principal(vendor, "VENDOR")));
        mvc.perform(get("/vendor/orders").cookie(cookie)).andExpect(status().isOk());
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", vendor);
        mvc.perform(get("/vendor/orders").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void confirmAndReadyPersistTrueFactsWithoutChangingPaymentOrStock() {
        authenticate(vendor);
        var beforePayments = paymentState(); var beforeStock = stockState();
        confirm();
        var confirmed = row();
        assertThat(confirmed).containsEntry("status", "CONFIRMED").containsEntry("version", 1L);
        var transition = jdbc.queryForMap("SELECT * FROM uteexpress.order_status_history WHERE order_id=? AND from_status IS NOT NULL", owned.getId());
        assertThat(transition).containsEntry("from_status", "NEW").containsEntry("to_status", "CONFIRMED")
                .containsEntry("actor_id", vendor);
        assertThat(transition.get("created_at")).isEqualTo(confirmed.get("updated_at"));
        assertThat(historyCount()).isEqualTo(2);
        lifecycle.markReady(new OrderReadyCommand(owned.getId(), 1L));
        assertThat(row()).containsEntry("status", "CONFIRMED").containsEntry("version", 2L);
        assertThat(row().get("ready_at")).isNotNull().isEqualTo(row().get("updated_at"));
        assertThat(historyCount()).isEqualTo(2); // ready is explicitly not a status transition
        assertThat(paymentState()).isEqualTo(beforePayments);
        assertThat(stockState()).isEqualTo(beforeStock);
        var stored = orders.findById(owned.getId()).orElseThrow();
        assertThat(stored.getShippingProviderId()).isEqualTo(owned.getShippingProviderId());
        assertThat(stored.getShippingServiceCode()).isEqualTo(owned.getShippingServiceCode());
        assertThat(stored.getShippingFee()).isEqualByComparingTo(owned.getShippingFee());
        rejects(() -> lifecycle.markReady(new OrderReadyCommand(owned.getId(), 2L)), ErrorCode.CONFLICT);
        rejects(this::confirm, ErrorCode.CONFLICT);
        assertThat(historyCount()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings = {"NEW", "PICKED_UP", "SHIPPING", "DELIVERED", "CANCELLED", "RETURN_REQUESTED", "RETURNED", "REFUNDED"})
    void readyRejectsInvalidSourceWithoutEffects(String status) {
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?", status, owned.getId());
        authenticate(vendor); var before = state();
        rejects(() -> lifecycle.markReady(new OrderReadyCommand(owned.getId(), 0L)), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void illegalEdgesAndNonVendorActionsCannotForceDelivery() {
        authenticate(vendor); confirm(); var before = state();
        for (OrderAction action : List.of(OrderAction.DELIVER, OrderAction.PICK_UP, OrderAction.START_SHIPPING,
                OrderAction.CANCEL_FAILED_DELIVERY, OrderAction.REJECT_RETURN, OrderAction.COMPLETE_REFUND)) {
            rejects(() -> lifecycle.transition(new OrderTransitionCommand(owned.getId(), OrderStatus.CONFIRMED, 1L, action, null)), ErrorCode.CONFLICT);
        }
        rejects(() -> lifecycle.transition(new OrderTransitionCommand(owned.getId(), OrderStatus.CONFIRMED, 1L, OrderAction.CONFIRM, null)), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"NEW", "CONFIRMED"})
    void cancellationRestoresPersistedQuantitiesOnceAndPreservesPaymentAndCart(String status) {
        authenticate(vendor);
        if (status.equals("CONFIRMED")) { confirm(); lifecycle.markReady(new OrderReadyCommand(owned.getId(), 1L)); }
        long version = status.equals("NEW") ? 0 : 2;
        var payment = paymentState(); var cartsBefore = cartState();
        cancel(OrderStatus.valueOf(status), version);
        assertThat(row()).containsEntry("status", "CANCELLED");
        assertThat(row()).containsEntry("shipping_provider_id", owned.getShippingProviderId())
                .containsEntry("shipping_service_code", owned.getShippingServiceCode());
        assertThat((BigDecimal) row().get("shipping_fee")).isEqualByComparingTo(owned.getShippingFee());
        assertThat(row().get("inventory_released_at")).isNotNull().isEqualTo(row().get("cancelled_at"));
        assertThat(stock(product)).isEqualTo(10); assertThat(stock(secondProduct)).isEqualTo(10);
        assertThat(historyCount()).isEqualTo(status.equals("NEW") ? 2 : 3);
        var after = state();
        rejects(() -> cancel(OrderStatus.valueOf(status), version), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(after);
        assertThat(paymentState()).isEqualTo(payment); assertThat(cartState()).isEqualTo(cartsBefore);
    }

    @ParameterizedTest @ValueSource(strings = {"PICKED_UP", "SHIPPING", "DELIVERED", "CANCELLED", "RETURN_REQUESTED", "RETURNED", "REFUNDED"})
    void lateCancellationNeverRestoresInventory(String status) throws Exception {
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?", status, owned.getId());
        var before = state();
        mvc.perform(post("/vendor/orders/{id}/cancel", owned.getId()).with(user(principal(vendor, "VENDOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(0)))
                .andExpect(status().isConflict());
        assertThat(state()).isEqualTo(before);
    }

    @Test void preexistingInventoryGuardPreventsAnyRelease() {
        jdbc.update("UPDATE uteexpress.orders SET inventory_released_at=CURRENT_TIMESTAMP WHERE id=?", owned.getId());
        authenticate(vendor); var before = state();
        rejects(() -> cancel(OrderStatus.NEW, 0), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void stockOverflowRollsBackEarlierProductRestorationAndAllOrderEffects() {
        jdbc.update("UPDATE uteexpress.products SET stock=2147483647 WHERE id=?", secondProduct);
        authenticate(vendor); var before = state();
        rejects(() -> cancel(OrderStatus.NEW, 0), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void historyFailureRollsBackFlushedStockAndStatusTogether() {
        jdbc.execute("ALTER TABLE uteexpress.order_status_history ADD CONSTRAINT ord03_test_history CHECK (to_status <> 'CANCELLED')");
        try {
            authenticate(vendor); var before = state();
            assertThatThrownBy(() -> cancel(OrderStatus.NEW, 0)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(state()).isEqualTo(before);
        } finally { jdbc.execute("ALTER TABLE uteexpress.order_status_history DROP CONSTRAINT ord03_test_history"); }
    }

    @Test void outerRollbackRollsBackReleaseTimestampHistoryAndStock() {
        authenticate(vendor); var before = state();
        transactions.executeWithoutResult(tx -> { cancel(OrderStatus.NEW, 0); tx.setRollbackOnly(); });
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"confirm", "ready", "cancel"})
    void foreignAndMissingMutationsAreSafeAndCannotForgeOwnership(String operation) throws Exception {
        var before = state();
        for (long id : List.of(foreign.getId(), Long.MAX_VALUE)) {
            mvc.perform(post("/vendor/orders/{id}/{operation}", id, operation).with(user(principal(vendor, "VENDOR")))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(0)).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            mvc.perform(post("/vendor/orders/{id}/{operation}", id, operation).with(user(principal(vendor, "VENDOR")))
                            .with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("expectedVersion", "0").param("reason", "UNABLE_TO_FULFILL")
                            .param("vendorId", "" + other).param("shopId", "" + otherShop))
                    .andExpect(status().isNotFound());
        }
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"confirm", "ready", "cancel"})
    void csrfAndHttpMethodsCannotBypassMutationRules(String operation) throws Exception {
        var before = state();
        mvc.perform(post("/vendor/orders/{id}/{operation}", owned.getId(), operation).with(user(principal(vendor, "VENDOR")))
                .contentType(MediaType.APPLICATION_JSON).content(body(0))).andExpect(status().isForbidden());
        mvc.perform(get("/vendor/orders/{id}/{operation}", owned.getId(), operation).with(user(principal(vendor, "VENDOR"))))
                .andExpect(status().isMethodNotAllowed());
        assertThat(state()).isEqualTo(before);
    }

    @Test void forgedJsonTargetIdentityAndQuantitiesAreRejected() throws Exception {
        var before = state();
        for (String field : List.of("status", "action", "shopId", "vendorId", "quantity", "paymentStatus")) {
            mvc.perform(post("/vendor/orders/{id}/confirm", owned.getId()).with(user(principal(vendor, "VENDOR")))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":0,\"" + field + "\":\"DELIVERED\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "9223372036854775808"})
    void malformedIdsAreControlledForReadsAndMutations(String id) throws Exception {
        for (var type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            mvc.perform(get("/vendor/orders/{id}", id).with(user(principal(vendor, "VENDOR"))).accept(type))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/vendor/orders/{id}/confirm", id).with(user(principal(vendor, "VENDOR")))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(0)).accept(type))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void missingNegativeOrStaleVersionAndUnsafeReasonProduceNoEffects() throws Exception {
        var before = state();
        for (String body : List.of("{}", "{\"expectedVersion\":-1}", "{\"expectedVersion\":1}")) {
            mvc.perform(post("/vendor/orders/{id}/confirm", owned.getId()).with(user(principal(vendor, "VENDOR")))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(400, 409));
        }
        authenticate(vendor);
        rejects(() -> lifecycle.transition(new OrderTransitionCommand(owned.getId(), OrderStatus.NEW, 0L,
                OrderAction.CANCEL_NEW, "Address or payment text")), ErrorCode.VALIDATION_FAILED);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "wrongAmount", "expired", "codPaid", "onlineUnpaid", "ambiguous"})
    void confirmationFailsClosedForInvalidPersistedPayment(String invalid) {
        switch (invalid) {
            case "missing" -> jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?", owned.getId());
            case "wrongAmount" -> jdbc.update("UPDATE uteexpress.payments SET amount=1 WHERE order_id=?", owned.getId());
            case "expired" -> jdbc.update("UPDATE uteexpress.payments SET expired_at=CURRENT_TIMESTAMP WHERE order_id=?", owned.getId());
            case "codPaid" -> jdbc.update("UPDATE uteexpress.payments SET status='PAID',paid_at=CURRENT_TIMESTAMP WHERE order_id=?", owned.getId());
            case "onlineUnpaid" -> jdbc.update("UPDATE uteexpress.payments SET method='ONLINE' WHERE order_id=?", owned.getId());
            case "ambiguous" -> jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',300000,?)", owned.getId(), UUID.randomUUID().toString());
        }
        authenticate(vendor); var before = state();
        rejects(this::confirm, ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void approvedPaidOnlineFactsCanConfirmWithoutPaymentMutationOrGateway() {
        jdbc.update("UPDATE uteexpress.payments SET method='ONLINE',status='PAID',paid_at=CURRENT_TIMESTAMP WHERE order_id=?", owned.getId());
        var before = paymentState(); authenticate(vendor); confirm();
        assertThat(paymentState()).isEqualTo(before); assertThat(row()).containsEntry("status", "CONFIRMED");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void paidOnlineRetryCanConfirmWithEarlierUnpaidAttempts(boolean expired) {
        jdbc.update("UPDATE uteexpress.payments SET method='ONLINE',expired_at=? WHERE order_id=?",
                expired ? Timestamp.from(CREATED.plusSeconds(60)) : null, owned.getId());
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at) VALUES (?,'ONLINE','PAID',300000,?,CURRENT_TIMESTAMP)",
                owned.getId(), UUID.randomUUID().toString());
        var beforePayment = paymentState(); var beforeStock = stockState();
        authenticate(vendor); confirm();
        assertThat(row()).containsEntry("status", "CONFIRMED").containsEntry("version", 1L);
        assertThat(historyCount()).isEqualTo(2);
        assertThat(paymentState()).isEqualTo(beforePayment);
        assertThat(stockState()).isEqualTo(beforeStock);
    }

    @ParameterizedTest @ValueSource(strings = {"wrongAmount", "expired", "missingPaidAt", "mixedMethods"})
    void onlineRetriesStillRequireUnambiguousValidPaidEvidence(String invalid) {
        jdbc.update("UPDATE uteexpress.payments SET method='ONLINE' WHERE order_id=?", owned.getId());
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at) VALUES (?,'ONLINE','PAID',300000,?,CURRENT_TIMESTAMP)",
                owned.getId(), UUID.randomUUID().toString());
        switch (invalid) {
            case "wrongAmount" -> jdbc.update("UPDATE uteexpress.payments SET amount=1 WHERE order_id=? AND status='PAID'", owned.getId());
            case "expired" -> jdbc.update("UPDATE uteexpress.payments SET expired_at=CURRENT_TIMESTAMP WHERE order_id=? AND status='PAID'", owned.getId());
            case "missingPaidAt" -> jdbc.update("UPDATE uteexpress.payments SET paid_at=NULL WHERE order_id=? AND status='PAID'", owned.getId());
            case "mixedMethods" -> jdbc.update("UPDATE uteexpress.payments SET method='COD' WHERE order_id=? AND status='UNPAID'", owned.getId());
        }
        authenticate(vendor); var before = state();
        rejects(this::confirm, ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void jsonMutationsAndFormReadyCancelPersistOnlyTheirIntendedEffects() throws Exception {
        mvc.perform(post("/vendor/orders/{id}/confirm", owned.getId()).with(user(principal(vendor, "VENDOR")))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(0)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("OK"));
        mvc.perform(post("/vendor/orders/{id}/ready", owned.getId()).with(user(principal(vendor, "VENDOR")))
                        .with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("expectedVersion", "1")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        assertThat(historyCount()).isEqualTo(2);
        mvc.perform(get("/vendor/orders/{id}", owned.getId()).with(user(principal(vendor, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("/ready"))));
        mvc.perform(post("/vendor/orders/{id}/cancel", owned.getId()).with(user(principal(vendor, "VENDOR")))
                        .with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("expectedVersion", "2")
                        .param("reason", "OUT_OF_STOCK").param("quantity", "10000").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        assertThat(row()).containsEntry("status", "CANCELLED").containsEntry("cancellation_reason", "Vendor cancellation: out of stock");
        assertThat(stock(product)).isEqualTo(10);
        assertThat(historyCount()).isEqualTo(3);
        mvc.perform(get("/vendor/orders/{id}", owned.getId()).with(user(principal(vendor, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("/cancel"))));
    }

    @Test void missingPersistedTimelineAndPaymentNeverProduceInferredFacts() throws Exception {
        jdbc.update("DELETE FROM uteexpress.order_status_history WHERE order_id=?", owned.getId());
        jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?", owned.getId());
        var before = state();
        mvc.perform(get("/vendor/orders/{id}", owned.getId()).with(user(principal(vendor, "VENDOR"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.facts.timeline").isEmpty())
                .andExpect(jsonPath("$.facts.payments").isEmpty());
        mvc.perform(get("/vendor/orders/{id}", owned.getId()).with(user(principal(vendor, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("UNPAID"))));
        assertThat(state()).isEqualTo(before);
    }

    @Test void invalidHtmlFormReturnsRenderableErrorAndCannotMutate() throws Exception {
        var before = state();
        mvc.perform(post("/vendor/orders/{id}/confirm", owned.getId()).with(user(principal(vendor, "VENDOR")))
                        .with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.TEXT_HTML))
                .andExpect(status().isBadRequest()).andExpect(view().name("order/error"));
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"LOCKED", "SUSPENDED"})
    void persistedAccountAndShopRestrictionsDenyMutations(String restriction) {
        if (restriction.equals("LOCKED")) jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", vendor);
        else jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED',moderation_reason='Test restriction' WHERE id=?", shop);
        authenticate(vendor); var before = state();
        rejects(this::confirm, ErrorCode.ACCESS_DENIED);
        rejects(() -> cancel(OrderStatus.NEW, 0), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
    }

    @Test void pagesEscapeSnapshotsShowOnlyValidActionsAndGetHasNoEffects() throws Exception {
        var before = state();
        mvc.perform(get("/vendor/orders").with(user(principal(vendor, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/vendor/orders/" + owned.getId())))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                        .contains("Đơn hàng của cửa hàng"));
        mvc.perform(get("/vendor/orders/{id}", owned.getId()).with(user(principal(vendor, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Snapshot &lt;script&gt;")))
                .andExpect(content().string(containsString("/confirm"))).andExpect(content().string(not(containsString("/ready"))))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                        .contains("Xác nhận đơn hàng", "Lý do hủy", "Hủy đơn hàng"));
        assertThat(state()).isEqualTo(before);
        mvc.perform(post("/vendor/orders/{id}/confirm", owned.getId()).with(user(principal(vendor, "VENDOR")))
                        .with(csrf()).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("expectedVersion", "0")
                        .param("status", "DELIVERED").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/vendor/orders/" + owned.getId()));
        mvc.perform(get("/vendor/orders/{id}", owned.getId()).with(user(principal(vendor, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/ready")))
                .andExpect(content().string(not(containsString("/confirm"))));
    }

    @ParameterizedTest @ValueSource(strings = {"confirm", "cancel", "ready", "competing"})
    void concurrentRequestsBlockInPostgresAndHaveOneWinningEffect(String kind) throws Exception {
        boolean ready = kind.equals("ready");
        if (ready) { authenticate(vendor); confirm(); }
        long version = ready ? 1 : 0;
        var executor = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();
        try {
            transactions.executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.users WHERE id=? FOR UPDATE", Long.class, vendor);
                for (int index = 0; index < 2; index++) {
                    boolean cancellation = kind.equals("cancel") || (kind.equals("competing") && index == 1);
                    futures.add(executor.submit(() -> {
                        authenticate(vendor);
                        try {
                            // Load before acquiring lifecycle locks to exercise managed stale-snapshot refresh.
                            return transactions.execute(workerTx -> {
                                orders.findById(owned.getId()).orElseThrow();
                                if (ready) lifecycle.markReady(new OrderReadyCommand(owned.getId(), version));
                                else if (cancellation) cancel(OrderStatus.NEW, version);
                                else confirm();
                                return "OK";
                            });
                        } catch (ApplicationException error) {
                            return error.errorCode().name();
                        } finally { SecurityContextHolder.clearContext(); }
                    }));
                }
                awaitBlockedVendors(2);
            });
            var results = new ArrayList<String>();
            for (var future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("OK", "CONFLICT");
        } finally { executor.shutdownNow(); }
        assertThat(historyCount()).isEqualTo(2);
        assertThat(row()).containsEntry("version", ready ? 2L : 1L);
        boolean cancelled = row().get("status").equals("CANCELLED");
        assertThat(stock(product)).isEqualTo(cancelled ? 10 : 8);
        assertThat(stock(secondProduct)).isEqualTo(cancelled ? 10 : 9);
        assertThat(paymentState()).allSatisfy(record -> assertThat(record).containsEntry("status", "UNPAID"));
    }

    @Test void checkoutDecrementAndCancellationRestoreUseTheSamePersistedQuantities() {
        jdbc.update("UPDATE uteexpress.products SET stock=10 WHERE id in (?,?)", product, secondProduct);
        long address = jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Receiver','0900000000','VN','District','Address',true) RETURNING id", Long.class, buyer);
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id", Long.class, "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',0,true)", provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7,?,?)", Timestamp.from(Instant.now().minusSeconds(60)), vendor);
        authenticate(buyer, "USER");
        carts.addProduct(new AddCartProductRequest(product, 2));
        carts.addProduct(new AddCartProductRequest(secondProduct, 1));
        var receipt = placement.placeOrder(new CheckoutRequest(UUID.randomUUID().toString(),
                List.of(new CheckoutRequest.Item(product, 2), new CheckoutRequest.Item(secondProduct, 1)),
                address, provider, "STANDARD", CheckoutRequest.PaymentMethod.COD, null));
        assertThat(stock(product)).isEqualTo(8); assertThat(stock(secondProduct)).isEqualTo(9);
        authenticate(vendor);
        lifecycle.transition(new OrderTransitionCommand(receipt.orderId(), OrderStatus.NEW, 0L, OrderAction.CANCEL_NEW, "UNABLE_TO_FULFILL"));
        assertThat(stock(product)).isEqualTo(10); assertThat(stock(secondProduct)).isEqualTo(10);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?", receipt.orderId()))
                .hasSize(1).allSatisfy(record -> assertThat(record).containsEntry("status", "UNPAID"));
    }

    @Test void concurrentModerationAuditForSameActorDoesNotDeadlockCancellation() throws Exception {
        var before = state();
        var executor = Executors.newFixedThreadPool(2);
        var shopLocked = new CountDownLatch(1);
        try {
            var moderationResult = executor.submit(() -> {
                authenticate(vendor, "ADMIN");
                try {
                    return transactions.execute(tx -> {
                        jdbc.queryForObject("SELECT id FROM uteexpress.shops WHERE id=? FOR UPDATE", Long.class, shop);
                        shopLocked.countDown();
                        awaitBlockedShop();
                        moderation.change(shop, 0L, true, "Concurrent restriction");
                        return "OK";
                    });
                } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(shopLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var cancellationResult = executor.submit(() -> {
                authenticate(vendor);
                try { cancel(OrderStatus.NEW, 0); return "OK"; }
                catch (ApplicationException error) { return error.errorCode().name(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(moderationResult.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
            // A previously managed Shop version may conflict before the refreshed restriction check.
            assertThat(cancellationResult.get(20, TimeUnit.SECONDS)).isIn("ACCESS_DENIED", "CONFLICT");
        } finally { executor.shutdownNow(); }
        assertThat(state()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shops WHERE id=?", String.class, shop)).isEqualTo("SUSPENDED");
    }

    private void confirm() { lifecycle.transition(new OrderTransitionCommand(owned.getId(), OrderStatus.NEW, 0L, OrderAction.CONFIRM, null)); }
    private void cancel(OrderStatus status, long version) { lifecycle.transition(new OrderTransitionCommand(owned.getId(), status, version, OrderAction.CANCEL_NEW.from() == status ? OrderAction.CANCEL_NEW : OrderAction.CANCEL_CONFIRMED, "UNABLE_TO_FULFILL")); }
    private long createUser() {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,'test-only-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class, name + "@example.test", name + "@example.test", name, name);
    }
    private long createShop(long owner) { return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, owner, UUID.randomUUID().toString()); }
    private long createProduct(long shopId, int stock) {
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id", Long.class, UUID.randomUUID().toString());
        return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',100000,?,'ACTIVE') RETURNING id", Long.class, shopId, category, stock);
    }
    private Order createOrder(long shopId, String code, Instant at) {
        long first = shopId == shop ? product : createProduct(shopId, 8);
        long second = shopId == shop ? secondProduct : createProduct(shopId, 9);
        var quote = new CheckoutQuote(shopId, List.of(
                new CheckoutQuote.ItemSnapshot(second, "Second", money("100000"), money("0"), money("100000"), 1, money("100000")),
                new CheckoutQuote.ItemSnapshot(first, "Snapshot <script>", money("100000"), money("0"), money("100000"), 2, money("200000"))),
                new CheckoutQuote.AddressSnapshot("Receiver <script>", "0900000000", "VN", "District", "Address"),
                OrderTotals.calculate(money("300000"), money("0"), money("0")), 901L, "STANDARD", null, money("0"), money("0"));
        var order = orders.saveAndFlush(new Order(buyer, code, UUID.randomUUID().toString(), "private-hash", quote, at));
        items.saveAllAndFlush(quote.items().stream().map(item -> new OrderItem(order.getId(), item)).toList());
        history.saveAndFlush(new OrderStatusHistory(order.getId(), null, OrderStatus.NEW, buyer, at, null));
        transactions.executeWithoutResult(tx -> payments.initializeCodForNewOrder(order.getId()));
        return order;
    }
    private Map<String, Object> row() { return jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", owned.getId()); }
    private long historyCount() { return jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=?", Long.class, owned.getId()); }
    private int stock(long id) { return jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, id); }
    private List<Map<String, Object>> paymentState() { return jdbc.queryForList("SELECT * FROM uteexpress.payments ORDER BY id"); }
    private List<Map<String, Object>> stockState() { return jdbc.queryForList("SELECT * FROM uteexpress.products ORDER BY id"); }
    private List<List<Map<String, Object>>> cartState() { return List.of(jdbc.queryForList("SELECT * FROM uteexpress.carts ORDER BY id"), jdbc.queryForList("SELECT * FROM uteexpress.cart_items ORDER BY id")); }
    private List<List<Map<String, Object>>> state() { return List.of(jdbc.queryForList("SELECT * FROM uteexpress.orders ORDER BY id"), jdbc.queryForList("SELECT * FROM uteexpress.order_status_history ORDER BY id"), paymentState(), stockState(), cartState().getFirst(), cartState().getLast()); }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static String body(long version) { return "{\"expectedVersion\":" + version + ",\"reason\":\"UNABLE_TO_FULFILL\"}"; }
    private static UteExpressPrincipal principal(long id, String role) { return new UteExpressPrincipal(id, "opaque-vendor-" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true); }
    private static void authenticate(long id) { authenticate(id, "VENDOR"); }
    private static void authenticate(long id, String role) {
        var principal = principal(id, role);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    private static void rejects(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).errorCode()).isEqualTo(code));
    }
    private void awaitBlockedVendors(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            var count = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%users%'", Long.class);
            if (count >= expected) return;
            try { Thread.sleep(20); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
        }
        throw new AssertionError("Both lifecycle requests must reach the PostgreSQL account row lock");
    }

    private void awaitBlockedShop() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            var count = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%shops%'", Long.class);
            if (count >= 1) return;
            try { Thread.sleep(20); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
        }
        throw new AssertionError("Vendor cancellation must reach the PostgreSQL shop row lock");
    }
}
