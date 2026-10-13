package com.uteexpress.order;

import com.uteexpress.checkout.dto.*;
import com.uteexpress.common.exception.*;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.order.service.*;
import com.uteexpress.payment.dto.CodCollectionCommand;
import com.uteexpress.payment.service.PaymentService;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.VoucherService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.jwt.JwtTokenService;
import com.uteexpress.shipping.service.*;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
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
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
@Import(ReturnManagementIT.Configuration.class)
class ReturnManagementIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    static final Instant DELIVERED = Instant.parse("2026-10-13T00:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired ReturnQueryService reads;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @MockitoSpyBean OrderStatusHistoryRepository history;
    @MockitoSpyBean ReturnOrderAuthority authority;
    ReturnOrderAuthority guardSpy;
    @Autowired PaymentService payments;
    @Autowired VoucherService vouchers;
    @Autowired ShipmentAssignmentService assignments;
    @Autowired ShipmentFulfillmentService shipments;
    @Autowired com.uteexpress.governance.service.AccountGovernanceService accountGovernance;
    @Autowired com.uteexpress.governance.service.RoleGovernanceService roleGovernance;
    @Autowired com.uteexpress.governance.service.ShopModerationService shopModeration;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @Autowired PlatformTransactionManager manager;
    @Autowired MutableClock clock;
    @Autowired Events events;
    TransactionTemplate tx;
    long buyer, vendor, otherBuyer, otherVendor, shipper, admin, shop, product, voucher, order, foreign;
    long deliveryVersion, initialHistory;

    @BeforeEach void setup() {
        clock.at = DELIVERED.minusSeconds(30);
        tx = new TransactionTemplate(manager);
        guardSpy = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(authority);
        buyer = createUser("USER"); vendor = createUser("VENDOR"); otherBuyer = createUser("USER");
        otherVendor = createUser("VENDOR"); shipper = createUser("SHIPPER"); admin = createUser("ADMIN");
        shop = shop(vendor);
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id",
                Long.class, UUID.randomUUID().toString());
        product = jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',100000,8,'ACTIVE') RETURNING id",
                Long.class, shop, category);
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id",
                Long.class, "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        String code = "R" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        voucher = jdbc.queryForObject("""
                INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by)
                VALUES (?,'SHOP','FIXED',?,10000,0,?,?,100,2,?) RETURNING id
                """, Long.class, code, shop, Timestamp.from(DELIVERED.minusSeconds(3600)),
                Timestamp.from(DELIVERED.plusSeconds(864000)), vendor);
        var quote = new CheckoutQuote(shop, List.of(new CheckoutQuote.ItemSnapshot(product, "Snapshot",
                money("100000"), money("0"), money("100000"), 1, money("100000"))),
                new CheckoutQuote.AddressSnapshot("Receiver", "0900000000", "VN", "District", "Address"),
                OrderTotals.calculate(money("100000"), money("10000"), money("15000")),
                provider, "STANDARD", null, money("0"), money("0"),
                new VoucherApplication(voucher, code, VoucherScope.SHOP, money("10000")));
        // Only NEW fixture creation is direct; delivery uses the real Order/Shipping/Payment APIs.
        order = createNew(buyer, quote);
        auth(vendor, "VENDOR");
        lifecycle.transition(new OrderTransitionCommand(order, OrderStatus.NEW, 0L, OrderAction.CONFIRM, null));
        lifecycle.markReady(new OrderReadyCommand(order, 1L));
        auth(admin, "ADMIN"); assignments.assign(order, shipper, 2L);
        auth(shipper, "SHIPPER");
        deliverAction(OrderAction.PICK_UP); deliverAction(OrderAction.START_SHIPPING);
        clock.at = DELIVERED; deliverAction(OrderAction.DELIVER);
        deliveryVersion = version(); initialHistory = historyCount();
        var otherQuote = new CheckoutQuote(shop(otherVendor), quote.items(), quote.address(), quote.totals(),
                provider, "STANDARD", null, money("0"), money("0"));
        foreign = createNew(otherBuyer, otherQuote);
        clock.at = DELIVERED.plusSeconds(60);
        auth(buyer, "USER"); events.values.clear();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void requestAndRejectionPreserveOriginalEvidenceAndAllOtherModules() {
        var side = sideEffects();
        var original = row().get("delivered_at");
        lifecycle.requestReturn(request());
        assertThat(orderStatus()).isEqualTo("RETURN_REQUESTED");
        assertThat(version()).isEqualTo(deliveryVersion + 1);
        assertThat(historyCount()).isEqualTo(initialHistory + 1);
        var evidence = evidence();
        assertThat(evidence).containsEntry("status", "PENDING").containsEntry("requested_by", buyer)
                .containsEntry("reason", "Wrong item").containsEntry("received_at", null).containsEntry("restockable", null);
        assertThat(events.values).hasSize(1);
        assertThat(events.values.getFirst().actorId()).isEqualTo(buyer);
        assertThat(sideEffects()).isEqualTo(side);
        auth(vendor, "VENDOR");
        lifecycle.rejectReturn(decision("  Cannot accept <script>  "));
        assertThat(orderStatus()).isEqualTo("DELIVERED");
        assertThat(row().get("delivered_at")).isEqualTo(original);
        assertThat(version()).isEqualTo(deliveryVersion + 2);
        assertThat(historyCount()).isEqualTo(initialHistory + 2);
        assertThat(evidence()).containsEntry("id", evidence.get("id")).containsEntry("status", "REJECTED")
                .containsEntry("reason", "Wrong item").containsEntry("decision_note", "Cannot accept <script>")
                .containsEntry("decided_by", vendor).containsEntry("version", 1L)
                .containsEntry("requested_at", evidence.get("requested_at"));
        var last = jdbc.queryForMap("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id DESC LIMIT 1", order);
        assertThat(last).containsEntry("from_status", "RETURN_REQUESTED").containsEntry("to_status", "DELIVERED")
                .containsEntry("actor_id", vendor).containsEntry("reason", "Vendor rejected return");
        assertThat(events.values).hasSize(2);
        assertThat(sideEffects()).isEqualTo(side);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order)).hasSize(1);
        auth(buyer, "USER");
        assertThat(reads.buyer(order).canRequest()).isFalse();
        var after = state();
        rejects(() -> lifecycle.requestReturn(new RequestReturnCommand(order, version(), "Again")), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(after);
    }

    @Test void approvalChangesOnlyReturnEvidenceAndCannotBeReversedOrRepeated() {
        lifecycle.requestReturn(request());
        var beforeOrder = row(); var side = sideEffects(); long count = historyCount();
        events.values.clear(); auth(vendor, "VENDOR");
        lifecycle.approveReturn(decision(null));
        assertThat(evidence()).containsEntry("status", "APPROVED").containsEntry("decided_by", vendor)
                .containsEntry("decision_note", null).containsEntry("received_at", null).containsEntry("version", 1L);
        assertThat(row()).isEqualTo(beforeOrder);
        assertThat(historyCount()).isEqualTo(count);
        assertThat(events.values).isEmpty();
        assertThat(sideEffects()).isEqualTo(side);
        var after = state();
        rejects(() -> lifecycle.approveReturn(decision(null)), ErrorCode.CONFLICT);
        rejects(() -> lifecycle.rejectReturn(decision("Changed mind")), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(after);
    }

    @ParameterizedTest @CsvSource({"-1,false", "0,true", "604799999999999,true",
            "604800000000000,true", "604800000000001,false"})
    void inclusiveUtcWindowUsesActualInstantBeforeTimestampRounding(long nanos, boolean allowed) {
        clock.at = DELIVERED.plusNanos(nanos);
        var before = state();
        assertThat(reads.buyer(order).canRequest()).isEqualTo(allowed);
        if (allowed) {
            lifecycle.requestReturn(request());
            assertThat(orderStatus()).isEqualTo("RETURN_REQUESTED");
        } else {
            rejects(() -> lifecycle.requestReturn(request()), ErrorCode.CONFLICT);
            assertThat(state()).isEqualTo(before);
        }
    }

    @Test void missingDeliveryTimeFailsClosedWithoutBackfill() {
        jdbc.update("UPDATE uteexpress.orders SET delivered_at=NULL WHERE id=?", order);
        var before = state();
        assertThat(reads.buyer(order).canRequest()).isFalse();
        rejects(() -> lifecycle.requestReturn(request()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"NEW", "CONFIRMED", "PICKED_UP", "SHIPPING", "CANCELLED", "RETURN_REQUESTED", "RETURNED", "REFUNDED"})
    void buyerCannotRequestFromOtherOrderStates(String status) {
        // Isolated invalid-state fixture: no production status writer is bypassed by the feature.
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?", status, order);
        var before = state();
        rejects(() -> lifecycle.requestReturn(request()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"application/json", "text/html"})
    void ownedReadsAndForeignSelectionsDoNotLeakEvidence(String type) throws Exception {
        lifecycle.requestReturn(request());
        for (long id : List.of(foreign, Long.MAX_VALUE, 0L, -1L)) {
            mvc.perform(get("/orders/{id}/return", id).with(user(principal(buyer, "USER"))).accept(type))
                    .andExpect(status().isNotFound()).andExpect(content().string(not(containsString("Wrong item"))));
            mvc.perform(get("/vendor/orders/{id}/return", id).with(user(principal(vendor, "VENDOR"))).accept(type))
                    .andExpect(status().isNotFound()).andExpect(content().string(not(containsString("Wrong item"))));
        }
        mvc.perform(get("/orders/{id}/return", order).with(user(principal(buyer, "USER"))).accept(type))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Wrong item")));
        mvc.perform(get("/vendor/orders/{id}/return", order).with(user(principal(vendor, "VENDOR"))).accept(type))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Wrong item")));
    }

    @Test void foreignWritesAreScopedBeforeStateOrEvidence() {
        var before = state();
        rejects(() -> lifecycle.requestReturn(new RequestReturnCommand(foreign, 0L, "Wrong item")), ErrorCode.RESOURCE_NOT_FOUND);
        auth(otherBuyer, "USER");
        rejects(() -> lifecycle.requestReturn(request()), ErrorCode.RESOURCE_NOT_FOUND);
        auth(buyer, "USER"); lifecycle.requestReturn(request()); auth(otherVendor, "VENDOR");
        var pending = state();
        rejects(() -> lifecycle.approveReturn(decision(null)), ErrorCode.RESOURCE_NOT_FOUND);
        rejects(() -> lifecycle.rejectReturn(decision("No")), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(state()).isEqualTo(pending);
        assertThat(sideEffects()).isEqualTo(before.get(3));
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER", "SHIPPER"})
    void nonBuyerRolesCannotReadOrRequestEvenWithCsrf(String role) throws Exception {
        mvc.perform(get("/orders/{id}/return", order).with(user(principal(buyer, role))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/orders/{id}/return", order).with(user(principal(buyer, role))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isForbidden());
    }

    @Test void userCannotMakeVendorDecisionsAndVendorCanRequestOwnPurchase() throws Exception {
        mvc.perform(post("/vendor/orders/{id}/return/approve", order).with(user(principal(buyer, "USER"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(decisionBody()))
                .andExpect(status().isForbidden());
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='VENDOR'", buyer);
        auth(buyer, "VENDOR");
        lifecycle.requestReturn(request());
        assertThat(orderStatus()).isEqualTo("RETURN_REQUESTED");
    }

    @Test void persistedEligibilityRejectsForgedPrincipalRolesAndLockedAccounts() {
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?", buyer);
        var before = state();
        rejects(() -> lifecycle.requestReturn(request()), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='USER'", buyer);
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", buyer);
        rejects(() -> lifecycle.requestReturn(request()), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"role", "account", "shop"})
    void vendorPersistedEligibilityIsRechecked(String restriction) {
        lifecycle.requestReturn(request()); auth(vendor, "VENDOR");
        if (restriction.equals("role")) jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?", vendor);
        if (restriction.equals("account")) jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", vendor);
        if (restriction.equals("shop")) jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED',moderation_reason='Test' WHERE id=?", shop);
        var before = state();
        rejects(() -> lifecycle.approveReturn(decision(null)), ErrorCode.ACCESS_DENIED);
        rejects(() -> lifecycle.rejectReturn(decision("No")), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
    }

    @Test void unauthenticatedAndNonPersistedPrincipalsAreDenied() throws Exception {
        mvc.perform(get("/orders/{id}/return", order)).andExpect(status().isUnauthorized());
        mvc.perform(post("/orders/{id}/return", order).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(requestBody()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/{id}/return", order).with(user("buyer").roles("USER")))
                .andExpect(status().isUnauthorized());
    }

    @Test void realJwtCookieAndCsrfUseTheExistingAuthenticationPipeline() throws Exception {
        SecurityContextHolder.clearContext();
        var cookie = new Cookie("UTEEXPRESS_AUTH", tokens.issue(principal(buyer, "USER")));
        mvc.perform(get("/orders/{id}/return", order).cookie(cookie).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canRequest").value(true));
        mvc.perform(post("/orders/{id}/return", order).cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(requestBody())).andExpect(status().isOk());
        var vendorCookie = new Cookie("UTEEXPRESS_AUTH", tokens.issue(principal(vendor, "VENDOR")));
        mvc.perform(post("/vendor/orders/{id}/return/reject", order).cookie(vendorCookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(decisionBodyWith("No")))
                .andExpect(status().isOk());
    }

    @Test void allMutationRoutesRequireCsrf() throws Exception {
        var before = state();
        mvc.perform(post("/orders/{id}/return", order).with(user(principal(buyer, "USER")))
                .contentType(MediaType.APPLICATION_JSON).content(requestBody())).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(before);
        auth(buyer, "USER"); lifecycle.requestReturn(request()); var pending = state();
        for (String operation : List.of("approve", "reject"))
            mvc.perform(post("/vendor/orders/{id}/return/" + operation, order).with(user(principal(vendor, "VENDOR")))
                    .contentType(MediaType.APPLICATION_JSON).content(decisionBodyWith("No"))).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(pending);
    }

    @ParameterizedTest @ValueSource(strings = {"buyerId", "vendorId", "actorId", "shopId", "status", "requestedAt", "deliveredAt", "decisionAt", "received", "restockable", "orderId"})
    void forgedJsonFormAndQueryFieldsAreRejected(String field) throws Exception {
        var before = state();
        mvc.perform(post("/orders/{id}/return", order).with(user(principal(buyer, "USER"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(requestBody().replace("}", ",\"" + field + "\":\"forged\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/orders/{id}/return", order).with(user(principal(buyer, "USER"))).with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("expectedVersion", "" + deliveryVersion)
                .param("reason", "Wrong item").param(field, "forged")).andExpect(status().isBadRequest());
        mvc.perform(get("/orders/{id}/return", order).with(user(principal(buyer, "USER")))
                .param(field, "forged").accept(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        assertThat(state()).isEqualTo(before);
        auth(buyer, "USER"); lifecycle.requestReturn(request()); var pending = state();
        for (String operation : List.of("approve", "reject")) {
            mvc.perform(post("/vendor/orders/{id}/return/" + operation, order).with(user(principal(vendor, "VENDOR"))).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(decisionBodyWith("No").replace("}", ",\"" + field + "\":\"forged\"}")))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/vendor/orders/{id}/return/" + operation, order).with(user(principal(vendor, "VENDOR"))).with(csrf())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("expectedVersion", "" + version())
                    .param("expectedRequestVersion", "0").param("reason", "No").param(field, "forged"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(state()).isEqualTo(pending);
    }

    @Test void formsRenderCsrfVersionsAndEscapeReasonsAcrossRequestAndRejection() throws Exception {
        mvc.perform(get("/orders/{id}/return", order).with(user(principal(buyer, "USER"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"expectedVersion\"")));
        mvc.perform(post("/orders/{id}/return", order).with(user(principal(buyer, "USER"))).with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("expectedVersion", "" + deliveryVersion).param("reason", "Wrong <script>"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/orders/" + order + "/return"));
        mvc.perform(get("/vendor/orders/{id}/return", order).with(user(principal(vendor, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Wrong &lt;script&gt;")))
                .andExpect(content().string(containsString("name=\"expectedRequestVersion\"")));
        mvc.perform(post("/vendor/orders/{id}/return/reject", order).with(user(principal(vendor, "VENDOR"))).with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("expectedVersion", "" + version()).param("expectedRequestVersion", "0").param("reason", "No <script>"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/orders/{id}/return", order).with(user(principal(buyer, "USER"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No &lt;script&gt;")))
                .andExpect(content().string(not(containsString("id=\"request-reason\""))));
    }

    @Test void approveFormDoesNotOfferReceiptOrRefundActions() throws Exception {
        lifecycle.requestReturn(request());
        mvc.perform(post("/vendor/orders/{id}/return/approve", order).with(user(principal(vendor, "VENDOR"))).with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("expectedVersion", "" + version())
                .param("expectedRequestVersion", "0")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/vendor/orders/{id}/return", order).with(user(principal(vendor, "VENDOR"))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("chưa được ghi nhận")))
                .andExpect(content().string(not(containsString("/return/receive"))))
                .andExpect(content().string(not(containsString("/return/refund"))));
        auth(vendor, "VENDOR"); var before = state();
        for (OrderAction action : List.of(OrderAction.RECEIVE_RETURN, OrderAction.COMPLETE_REFUND))
            rejects(() -> lifecycle.transition(new OrderTransitionCommand(order, OrderStatus.RETURN_REQUESTED, version(), action, null)),
                    ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void expectedVersionsAndPendingEvidenceAreRequired() {
        rejects(() -> lifecycle.requestReturn(new RequestReturnCommand(order, deliveryVersion - 1, "No")), ErrorCode.CONFLICT);
        auth(vendor, "VENDOR");
        rejects(() -> lifecycle.approveReturn(decision(null)), ErrorCode.CONFLICT);
        auth(buyer, "USER"); lifecycle.requestReturn(request()); auth(vendor, "VENDOR");
        var before = state();
        rejects(() -> lifecycle.approveReturn(new ReturnDecisionCommand(order, version() - 1, 0L, null)), ErrorCode.CONFLICT);
        rejects(() -> lifecycle.rejectReturn(new ReturnDecisionCommand(order, version(), 1L, "No")), ErrorCode.CONFLICT);
        rejects(() -> lifecycle.approveReturn(new ReturnDecisionCommand(order, version(), 0L, "Unexpected")), ErrorCode.VALIDATION_FAILED);
        rejects(() -> lifecycle.rejectReturn(decision(" \t\n ")), ErrorCode.VALIDATION_FAILED);
        rejects(() -> lifecycle.rejectReturn(decision("x".repeat(1001))), ErrorCode.VALIDATION_FAILED);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"request", "approve", "reject"})
    void outerTransactionsAreRejectedBeforeAnyMutation(String operation) {
        if (!operation.equals("request")) { lifecycle.requestReturn(request()); auth(vendor, "VENDOR"); }
        var before = state();
        tx.executeWithoutResult(s -> rejects(() -> {
            switch (operation) {
                case "request" -> lifecycle.requestReturn(request());
                case "approve" -> lifecycle.approveReturn(decision(null));
                case "reject" -> lifecycle.rejectReturn(decision("No"));
                default -> throw new AssertionError(operation);
            }
        }, ErrorCode.INVALID_REQUEST));
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"request", "reject"})
    void historyFailureRollsBackEvidenceOrderAndEvents(String operation) {
        if (operation.equals("reject")) { lifecycle.requestReturn(request()); auth(vendor, "VENDOR"); }
        var before = state(); events.values.clear();
        doThrow(new DataIntegrityViolationException("test-only-history-failure")).when(history).saveAndFlush(any(OrderStatusHistory.class));
        rejects(() -> {
            if (operation.equals("request")) lifecycle.requestReturn(request());
            else lifecycle.rejectReturn(decision("No"));
        }, ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        assertThat(events.values).isEmpty();
    }

    @Test void approvalFailureAfterEvidenceFlushRollsBackWithoutOrderEffects() {
        lifecycle.requestReturn(request()); auth(vendor, "VENDOR");
        var before = state(); events.values.clear();
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("test-only-after-evidence"); })
                .when(guardSpy).decide(any(), eq(true), isNull(), any());
        assertThatThrownBy(() -> lifecycle.approveReturn(decision(null))).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo(before); assertThat(events.values).isEmpty();
    }

    @Test void databaseUniqueConstraintRetainsRejectedHistory() {
        lifecycle.requestReturn(request()); auth(vendor, "VENDOR"); lifecycle.rejectReturn(decision("No"));
        var before = state();
        assertThatThrownBy(() -> insertEvidence(order, buyer, "PENDING", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"foreignOrder", "foreignBuyer", "foreignDecider", "status", "pendingDecision",
            "rejectedNote", "approvedNote", "receipt", "restockable", "version", "reason", "times"})
    void databaseRejectsInvalidForeignKeysAndEvidence(String invalid) {
        if (invalid.equals("foreignOrder")) {
            assertThatThrownBy(() -> insertEvidence(Long.MAX_VALUE, buyer, "PENDING", null, null, null))
                    .isInstanceOf(DataIntegrityViolationException.class); return;
        }
        if (invalid.equals("foreignBuyer")) {
            assertThatThrownBy(() -> insertEvidence(order, Long.MAX_VALUE, "PENDING", null, null, null))
                    .isInstanceOf(DataIntegrityViolationException.class); return;
        }
        insertEvidence(order, buyer, "PENDING", null, null, null);
        String change = switch (invalid) {
            case "foreignDecider" -> "status='APPROVED',decided_by=9223372036854775807,decision_at=requested_at,updated_at=requested_at";
            case "status" -> "status='RECEIVED'";
            case "pendingDecision" -> "decided_by=" + vendor;
            case "rejectedNote" -> "status='REJECTED',decided_by=" + vendor + ",decision_at=requested_at,decision_note='   '";
            case "approvedNote" -> "status='APPROVED',decided_by=" + vendor + ",decision_at=requested_at,decision_note='No'";
            case "receipt" -> "received_at=requested_at";
            case "restockable" -> "restockable=false";
            case "version" -> "version=-1";
            case "reason" -> "reason=E'\\t\\n '";
            case "times" -> "updated_at=requested_at-interval '1 second'";
            default -> throw new AssertionError(invalid);
        };
        assertThatThrownBy(() -> jdbc.update("UPDATE uteexpress.return_requests SET " + change + " WHERE order_id=?", order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Timeout(40)
    void concurrentRequestsWaitOnOrderAndCommitExactlyOnce(boolean firstRollsBack) throws Exception {
        race(false, false, firstRollsBack);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Timeout(40)
    void concurrentApproveRejectWaitOnOrderAndHaveOneWinner(boolean approvalWins) throws Exception {
        lifecycle.requestReturn(request());
        events.values.clear();
        race(true, approvalWins, false);
    }

    private void race(boolean decisions, boolean approvalWins, boolean rollback) throws Exception {
        var side = sideEffects();
        long ov = version(), historyBefore = historyCount();
        var pids = new ConcurrentHashMap<String, Integer>();
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> {
            jdbc.execute("SET LOCAL lock_timeout='15s'");
            jdbc.execute("SET LOCAL statement_timeout='20s'");
            pids.put(Thread.currentThread().getName(), pid());
            return call.callRealMethod();
        }).when(guardSpy).lockBuyer(anyLong());
        doAnswer(call -> {
            jdbc.execute("SET LOCAL lock_timeout='15s'");
            jdbc.execute("SET LOCAL statement_timeout='20s'");
            pids.put(Thread.currentThread().getName(), pid());
            return call.callRealMethod();
        }).when(guardSpy).lockVendor(anyLong());
        org.mockito.stubbing.Answer<Object> hold = call -> {
            var result = call.callRealMethod();
            if (Thread.currentThread().getName().equals("return-first")) {
                held.countDown(); await(release);
                if (rollback) throw new IllegalStateException("test-only-rollback");
            }
            return result;
        };
        if (decisions) doAnswer(hold).when(guardSpy).decide(any(), anyBoolean(), nullable(String.class), any());
        else doAnswer(hold).when(guardSpy).create(any(), anyString(), any());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> raceWorker("return-first", decisions, approvalWins, ov));
            await(held);
            assertThat(events.values).isEmpty();
            var second = pool.submit(() -> raceWorker("return-second", decisions, !approvalWins, ov));
            awaitPid(pids, "return-second");
            awaitBlocked(pids.get("return-second"), pids.get("return-first"), "orders");
            release.countDown();
            assertThat(first.get(25, TimeUnit.SECONDS)).isEqualTo(rollback ? "ROLLBACK" : "OK");
            assertThat(second.get(25, TimeUnit.SECONDS)).isEqualTo(rollback ? "OK" : "CONFLICT");
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order)).hasSize(1);
        if (decisions) {
            assertThat(evidence().get("status")).isEqualTo(approvalWins ? "APPROVED" : "REJECTED");
            assertThat(version()).isEqualTo(ov + (approvalWins ? 0 : 1));
            assertThat(historyCount()).isEqualTo(historyBefore + (approvalWins ? 0 : 1));
            assertThat(events.values).hasSize(approvalWins ? 0 : 1);
        } else {
            assertThat(evidence().get("status")).isEqualTo("PENDING");
            assertThat(version()).isEqualTo(ov + 1);
            assertThat(historyCount()).isEqualTo(historyBefore + 1);
            assertThat(events.values).hasSize(1);
        }
        assertThat(sideEffects()).isEqualTo(side);
    }

    private String raceWorker(String name, boolean decisions, boolean approve, long ov) {
        Thread.currentThread().setName(name);
        auth(decisions ? vendor : buyer, decisions ? "VENDOR" : "USER");
        try {
            if (!decisions) lifecycle.requestReturn(new RequestReturnCommand(order, ov, "Wrong item"));
            else if (approve) lifecycle.approveReturn(new ReturnDecisionCommand(order, ov, 0L, null));
            else lifecycle.rejectReturn(new ReturnDecisionCommand(order, ov, 0L, "No"));
            return "OK";
        } catch (ApplicationException e) { return e.errorCode().name(); }
        catch (IllegalStateException e) {
            if (!"test-only-rollback".equals(e.getMessage())) throw e;
            return "ROLLBACK";
        } finally { SecurityContextHolder.clearContext(); }
    }

    @ParameterizedTest @ValueSource(strings = {"buyerAccount", "buyerRole", "vendorAccount", "vendorShop"}) @Timeout(40)
    void earlierRestrictionCommitDeniesWaitingReturnWithoutPartialEffects(String restriction) throws Exception {
        boolean vendorAction = restriction.startsWith("vendor");
        if (vendorAction) lifecycle.requestReturn(request());
        var beforeOrder = row();
        var beforeEvidence = jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order);
        var beforeHistory = jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", order);
        events.values.clear();
        var pids = new ConcurrentHashMap<String, Integer>();
        doAnswer(call -> { pids.put("worker", pid()); jdbc.execute("SET LOCAL lock_timeout='15s'"); return call.callRealMethod(); })
                .when(guardSpy).lockBuyer(anyLong());
        doAnswer(call -> { pids.put("worker", pid()); jdbc.execute("SET LOCAL lock_timeout='15s'"); return call.callRealMethod(); })
                .when(guardSpy).lockVendor(anyLong());
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> worker = tx.execute(s -> {
                auth(admin, "ADMIN"); int holder = pid();
                restrict(restriction);
                var future = pool.submit(() -> {
                    auth(vendorAction ? vendor : buyer, vendorAction ? "VENDOR" : "USER");
                    try {
                        if (vendorAction) lifecycle.rejectReturn(decision("No"));
                        else lifecycle.requestReturn(request());
                        return "OK";
                    } catch (ApplicationException e) { return e.errorCode().name(); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                awaitPid(pids, "worker");
                awaitBlocked(pids.get("worker"), holder, restriction.equals("vendorShop") ? "shops" : "users");
                return future;
            });
            if (restriction.equals("vendorShop"))
                assertThat(worker.get(25, TimeUnit.SECONDS)).isIn("ACCESS_DENIED", "CONFLICT");
            else assertThat(worker.get(25, TimeUnit.SECONDS)).isEqualTo("ACCESS_DENIED");
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(row()).isEqualTo(beforeOrder);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order)).isEqualTo(beforeEvidence);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", order)).isEqualTo(beforeHistory);
        assertThat(events.values).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"buyerAccount", "buyerRole", "vendorAccount", "vendorShop"}) @Timeout(40)
    void laterRestrictionWaitsForReturnEligibilityThroughCommit(String restriction) throws Exception {
        boolean vendorAction = restriction.startsWith("vendor");
        if (vendorAction) lifecycle.requestReturn(request());
        events.values.clear();
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var holderPid = new CompletableFuture<Integer>(); var waiterPid = new CompletableFuture<Integer>();
        org.mockito.stubbing.Answer<Object> hold = call -> {
            var result = call.callRealMethod(); holderPid.complete(pid()); held.countDown(); await(release);
            assertThat(events.values).isEmpty();
            return result;
        };
        if (vendorAction) doAnswer(hold).when(guardSpy).decide(any(), eq(false), anyString(), any());
        else doAnswer(hold).when(guardSpy).create(any(), anyString(), any());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> raceWorker("return-holder", vendorAction, false, vendorAction ? version() : deliveryVersion));
            await(held);
            var second = pool.submit(() -> {
                auth(admin, "ADMIN");
                try {
                    tx.executeWithoutResult(s -> {
                        jdbc.execute("SET LOCAL lock_timeout='15s'");
                        waiterPid.complete(pid()); restrict(restriction);
                    });
                } finally { SecurityContextHolder.clearContext(); }
            });
            awaitBlocked(waiterPid.get(10, TimeUnit.SECONDS), holderPid.get(10, TimeUnit.SECONDS),
                    restriction.equals("vendorShop") ? "shops" : "users");
            release.countDown();
            assertThat(first.get(25, TimeUnit.SECONDS)).isEqualTo("OK");
            second.get(25, TimeUnit.SECONDS);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(orderStatus()).isEqualTo(vendorAction ? "DELIVERED" : "RETURN_REQUESTED");
        assertThat(events.values).hasSize(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Timeout(40)
    void shippingAndReturnBothStartAtOrderWithoutAccountInversion(boolean shippingFirst) throws Exception {
        var side = sideEffects();
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var holder = new CompletableFuture<Integer>(); var waiter = new CompletableFuture<Integer>();
        if (!shippingFirst) doAnswer(call -> {
            var result = call.callRealMethod(); holder.complete(pid()); held.countDown(); await(release); return result;
        }).when(guardSpy).create(any(), anyString(), any());
        if (shippingFirst) doAnswer(call -> {
            waiter.complete(pid()); jdbc.execute("SET LOCAL lock_timeout='15s'"); return call.callRealMethod();
        }).when(guardSpy).lockBuyer(anyLong());
        long sv = jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?", Long.class, order);
        var pool = Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<String> returnCall = () -> raceWorker("return-shipping-race", false, false, deliveryVersion);
        java.util.concurrent.Callable<String> shippingCall = () -> {
            auth(shipper, "SHIPPER");
            try {
                tx.executeWithoutResult(s -> {
                    jdbc.execute("SET LOCAL lock_timeout='15s'");
                    if (!shippingFirst) waiter.complete(pid());
                    shipments.requireFulfillmentEvidence(order, sv, com.uteexpress.shipping.dto.ShipmentStatus.DELIVERED);
                    if (shippingFirst) { holder.complete(pid()); held.countDown(); await(release); }
                });
                return "OK";
            } finally { SecurityContextHolder.clearContext(); }
        };
        try {
            var first = pool.submit(shippingFirst ? shippingCall : returnCall);
            await(held);
            var second = pool.submit(shippingFirst ? returnCall : shippingCall);
            awaitBlocked(waiter.get(10, TimeUnit.SECONDS), holder.get(10, TimeUnit.SECONDS), "orders");
            release.countDown();
            assertThat(first.get(25, TimeUnit.SECONDS)).isEqualTo("OK");
            assertThat(second.get(25, TimeUnit.SECONDS)).isEqualTo("OK");
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
        assertThat(orderStatus()).isEqualTo("RETURN_REQUESTED");
        assertThat(events.values).hasSize(1);
        assertThat(sideEffects()).isEqualTo(side);
    }

    private void restrict(String restriction) {
        switch (restriction) {
            case "buyerAccount", "vendorAccount" -> {
                long actor = restriction.equals("buyerAccount") ? buyer : vendor;
                long uv = jdbc.queryForObject("SELECT version FROM uteexpress.users WHERE id=?", Long.class, actor);
                accountGovernance.setLocked(actor, uv, true);
            }
            case "buyerRole" -> {
                long uv = jdbc.queryForObject("SELECT version FROM uteexpress.users WHERE id=?", Long.class, buyer);
                roleGovernance.change(buyer, uv, com.uteexpress.governance.dto.ManagedRole.USER, false);
            }
            case "vendorShop" -> {
                long sv = jdbc.queryForObject("SELECT version FROM uteexpress.shops WHERE id=?", Long.class, shop);
                shopModeration.change(shop, sv, true, "Test restriction");
            }
            default -> throw new AssertionError(restriction);
        }
    }

    @Test void migrationValidatesAndSecondMigrateHasNoPendingWork() {
        var flyway = org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()).schemas("uteexpress").defaultSchema("uteexpress").load();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.flyway_schema_history WHERE version='20261013090000' AND success", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"request", "reject"})
    void failureAtCommitRollsBackWrittenHistoryAndSuppressesRegisteredEvent(String operation) {
        if (operation.equals("reject")) { lifecycle.requestReturn(request()); auth(vendor, "VENDOR"); }
        var before = state(); events.values.clear();
        org.mockito.stubbing.Answer<Object> failCommit = call -> {
            var result = call.callRealMethod();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            assertThat(historyCount()).isEqualTo(((List<?>) before.get(2)).size() + 1);
                            assertThat(events.values).isEmpty();
                            throw new IllegalStateException("test-only-before-commit");
                        }
                    });
            return result;
        };
        if (operation.equals("request")) doAnswer(failCommit).when(guardSpy).create(any(), anyString(), any());
        else doAnswer(failCommit).when(guardSpy).decide(any(), eq(false), anyString(), any());
        assertThatThrownBy(() -> {
            if (operation.equals("request")) lifecycle.requestReturn(request());
            else lifecycle.rejectReturn(decision("No"));
        }).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo(before);
        assertThat(events.values).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"missingVersion", "negativeVersion", "blankReason", "longReason", "duplicateVersion", "invalidId"})
    void malformedBuyerCommandsHaveSafeJsonAndHtmlErrors(String invalid) throws Exception {
        var before = state();
        String input = switch (invalid) {
            case "missingVersion" -> "{\"reason\":\"No\"}";
            case "negativeVersion" -> "{\"expectedVersion\":-1,\"reason\":\"No\"}";
            case "blankReason" -> "{\"expectedVersion\":" + deliveryVersion + ",\"reason\":\" \"}";
            case "longReason" -> "{\"expectedVersion\":" + deliveryVersion + ",\"reason\":\"" + "x".repeat(1001) + "\"}";
            default -> requestBody();
        };
        for (String type : List.of("application/json", "text/html")) {
            var call = post("/orders/{id}/return", invalid.equals("invalidId") ? "abc" : order)
                    .with(user(principal(buyer, "USER"))).with(csrf()).accept(type);
            if (invalid.equals("duplicateVersion")) {
                call.contentType(MediaType.APPLICATION_FORM_URLENCODED).param("expectedVersion", "" + deliveryVersion, "" + deliveryVersion)
                        .param("reason", "No");
            } else call.contentType(MediaType.APPLICATION_JSON).content(input);
            mvc.perform(call).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(type))
                    .andExpect(content().string(not(containsString("fixture-hash"))));
        }
        assertThat(state()).isEqualTo(before);
    }

    @Test void originalBuyerMismatchCannotBeInsertedEvenForExistingUser() {
        assertThatThrownBy(() -> insertEvidence(order, otherBuyer, "PENDING", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order)).isEmpty();
    }

    private int pid() { return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class); }
    private void awaitBlocked(int waiter, int holder, String table) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            var row = jdbc.queryForMap("SELECT wait_event_type,query FROM pg_stat_activity WHERE pid=?", waiter);
            var blockers = jdbc.queryForList("SELECT unnest(pg_blocking_pids(?))", Integer.class, waiter);
            if ("Lock".equals(row.get("wait_event_type")) && blockers.contains(holder)) {
                assertThat(row.get("query").toString().toLowerCase(Locale.ROOT)).contains(table);
                return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("Expected a real PostgreSQL " + table + " waiter blocked by PID " + holder);
    }
    private static void awaitPid(Map<String, Integer> pids, String key) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!pids.containsKey(key) && System.nanoTime() < deadline) Thread.onSpinWait();
        assertThat(pids).containsKey(key);
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    private long createNew(long buyerId, CheckoutQuote quote) {
        auth(buyerId, "USER");
        return tx.execute(s -> {
            if (quote.voucher() != null)
                vouchers.lockForCheckout(quote.voucher().code(), quote.shopId(), quote.totals().subtotal());
            Order entity = orders.saveAndFlush(new Order(buyerId, "ORD-" + UUID.randomUUID(),
                    UUID.randomUUID().toString(), "fixture-hash", quote, clock.instant()));
            items.saveAllAndFlush(quote.items().stream().map(i -> new OrderItem(entity.getId(), i)).toList());
            history.saveAndFlush(new OrderStatusHistory(entity.getId(), null, OrderStatus.NEW, buyerId, clock.instant(), null));
            if (quote.voucher() != null) vouchers.recordUsage(quote.voucher(), entity.getId());
            payments.initializeCodForNewOrder(entity.getId());
            return entity.getId();
        });
    }
    private void deliverAction(OrderAction action) {
        long ov = version(), sv = jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?", Long.class, order);
        tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, ov, sv, action);
            switch (action) {
                case PICK_UP -> shipments.recordPickedUp(order, sv);
                case START_SHIPPING -> shipments.recordShipping(order, sv);
                case DELIVER -> {
                    payments.collectCod(new CodCollectionCommand(order, money("105000")));
                    shipments.recordDelivered(order, sv);
                }
                default -> throw new AssertionError(action);
            }
            lifecycle.completeShipperTransition(order, ov, sv, action);
        });
    }
    private long createUser(String role) {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,'test-only-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, name + "@example.test", name + "@example.test", name, name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role);
        return id;
    }
    private long shop(long owner) {
        return jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id",
                Long.class, owner, UUID.randomUUID().toString());
    }
    private void insertEvidence(long orderId, long buyerId, String status, Long decider, Instant decisionAt, String note) {
        jdbc.update("""
                INSERT INTO uteexpress.return_requests(order_id,requested_by,reason,status,requested_at,created_at,updated_at,decided_by,decision_at,decision_note)
                VALUES (?,?,'Reason',?,?,?,?,?,?,?)
                """, orderId, buyerId, status, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()), decider, decisionAt == null ? null : Timestamp.from(decisionAt), note);
    }
    private RequestReturnCommand request() { return new RequestReturnCommand(order, deliveryVersion, "  Wrong item  "); }
    private ReturnDecisionCommand decision(String reason) { return new ReturnDecisionCommand(order, version(), 0L, reason); }
    private String requestBody() { return "{\"expectedVersion\":" + deliveryVersion + ",\"reason\":\"Wrong item\"}"; }
    private String decisionBody() { return "{\"expectedVersion\":" + version() + ",\"expectedRequestVersion\":0}"; }
    private String decisionBodyWith(String reason) { return decisionBody().replace("}", ",\"reason\":\"" + reason + "\"}"); }
    private Map<String, Object> row() { return jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order); }
    private Map<String, Object> evidence() { return jdbc.queryForMap("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order); }
    private long version() { return jdbc.queryForObject("SELECT version FROM uteexpress.orders WHERE id=?", Long.class, order); }
    private String orderStatus() { return jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order); }
    private long historyCount() { return jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=?", Long.class, order); }
    private List<Object> sideEffects() {
        return List.of(jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=? ORDER BY id", order),
                jdbc.queryForList("SELECT * FROM uteexpress.shipments WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.products WHERE id=?", product),
                jdbc.queryForList("SELECT * FROM uteexpress.vouchers WHERE id=?", voucher),
                jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.order_items WHERE order_id=? ORDER BY id", order),
                jdbc.queryForList("SELECT * FROM uteexpress.audit_logs ORDER BY id"),
                jdbc.queryForList("SELECT * FROM uteexpress.shipment_assignment_history ORDER BY id"));
    }
    private List<Object> state() {
        return List.of(row(), jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", order), sideEffects());
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static UteExpressPrincipal principal(long id, String role) {
        return new UteExpressPrincipal(id, "opaque", null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true);
    }
    private static void auth(long id, String role) {
        var p = principal(id, role);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
    }
    private static void rejects(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
    }

    @TestConfiguration
    static class Configuration {
        @Bean @Primary MutableClock returnClock() { return new MutableClock(); }
        @Bean Events returnEvents() { return new Events(); }
    }
    static class MutableClock extends Clock {
        volatile Instant at = DELIVERED;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return at; }
    }
    static class Events {
        final List<OrderStatusChangedEvent> values = new CopyOnWriteArrayList<>();
        @EventListener public void onEvent(OrderStatusChangedEvent event) { values.add(event); }
    }
}
