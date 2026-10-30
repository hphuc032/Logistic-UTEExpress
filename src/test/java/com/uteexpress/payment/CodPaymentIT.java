package com.uteexpress.payment;

import com.uteexpress.checkout.dto.CheckoutQuote;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.payment.dto.CodCollectionCommand;
import com.uteexpress.payment.dto.PaymentRecordView;
import com.uteexpress.payment.dto.PaymentStatus;
import com.uteexpress.payment.entity.Payment;
import com.uteexpress.payment.service.PaymentService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class CodPaymentIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PaymentService payments;
    @Autowired OrderRepository orders;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    long buyer, other, orderId;
    TransactionTemplate transactions;

    @BeforeEach void setup() {
        for (String table : List.of("order_status_history", "payments", "order_items", "orders")) {
            jdbc.update("DELETE FROM uteexpress." + table);
        }
        buyer = createUser();
        other = createUser();
        transactions = new TransactionTemplate(transactionManager);
        orderId = createOrder(buyer);
    }

    private long createOrder(long owner) {
        var totals = OrderTotals.calculate(money("100000"), money("10000"), money("17000"));
        var quote = new CheckoutQuote(901L,
                List.of(new CheckoutQuote.ItemSnapshot(902L, "Persisted snapshot", money("100000"),
                        money("0"), money("100000"), 1, money("100000"))),
                new CheckoutQuote.AddressSnapshot("Receiver", "0900000000", "VN", "District", "Address"),
                totals, 903L, "STANDARD", null, money("0"), money("0"));
        return orders.saveAndFlush(new Order(owner, "PAY-" + UUID.randomUUID(), UUID.randomUUID().toString(),
                "private-hash", quote, Instant.now().minusSeconds(60))).getId();
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void initializationPersistsUnpaidCodFromOrderTotalsAndCannotDuplicate() {
        var view = initialize();
        assertThat(view.method().name()).isEqualTo("COD");
        assertThat(view.status()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(view.amount()).isEqualByComparingTo("107000");
        assertThat(paymentRow()).containsEntry("paid_at", null).containsEntry("provider_reference", null);
        assertThat(paymentRow().get("created_at")).isEqualTo(paymentRow().get("updated_at"));
        var before = state();
        rejects(() -> initialize(), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test void exactCollectionPersistsPaidUsingServerTimeAndPreservesUnrelatedState() {
        ready();
        var beforeOrder = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", orderId);
        var view = collect("107000.000");
        assertThat(view.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(view.amount()).isEqualByComparingTo("107000");
        assertThat(paymentRow().get("paid_at")).isNotNull().isEqualTo(paymentRow().get("updated_at"));
        assertThat(paymentRow()).containsEntry("provider_reference", null);
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", orderId)).isEqualTo(beforeOrder);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.payments WHERE order_id=? AND status='PAID'", Long.class, orderId))
                .isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"106999", "107001", "0"})
    void underpaymentOverpaymentAndZeroLeaveEntireAggregateUnchanged(String amount) {
        ready();
        var before = state();
        rejects(() -> collect(amount), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        assertThat(paymentRow()).containsEntry("status", "UNPAID").containsEntry("paid_at", null);
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "107000.01", "100000000000000000"}) @NullSource
    void invalidAmountsAreRejectedBeforeMutation(String amount) {
        ready();
        var before = state();
        rejects(() -> collect(amount), ErrorCode.VALIDATION_FAILED);
        assertThat(state()).isEqualTo(before);
    }

    @Test void repeatConflictsAndPreservesFirstPaidTimestampAndAmount() {
        ready();
        collect("107000");
        var before = state();
        rejects(() -> collect("107000"), ErrorCode.CONFLICT);
        rejects(() -> collect("107001"), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void collectionNeverChangesAnotherOrdersPayment() {
        ready();
        long foreignOrder = createOrder(other);
        transactions.executeWithoutResult(tx -> payments.initializeCodForNewOrder(foreignOrder));
        var before = jdbc.queryForMap("SELECT * FROM uteexpress.payments WHERE order_id=?", foreignOrder);
        collect("107000");
        assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.payments WHERE order_id=?", foreignOrder)).isEqualTo(before);
        assertThat(paymentRow()).containsEntry("status", "PAID");
    }

    @ParameterizedTest @ValueSource(strings = {"payment", "order"})
    void persistedOrderAmountIsAuthoritativeAndDriftIsNeverSilentlyReconciled(String source) {
        ready();
        if (source.equals("payment")) jdbc.update("UPDATE uteexpress.payments SET amount=108000 WHERE order_id=?", orderId);
        else jdbc.update("UPDATE uteexpress.orders SET shipping_fee=18000,grand_total=108000 WHERE id=?", orderId);
        var before = state();
        rejects(() -> collect("107000"), ErrorCode.CONFLICT);
        rejects(() -> collect("108000"), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"NEW", "CONFIRMED", "PICKED_UP", "DELIVERED", "CANCELLED", "RETURN_REQUESTED", "RETURNED", "REFUNDED"})
    void ineligibleOrderStateCannotCollect(String status) {
        initialize();
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?", status, orderId);
        var before = state();
        rejects(() -> collect("107000"), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"ONLINE", "EXPIRED", "MULTIPLE"})
    void ineligibleOrAmbiguousPaymentRecordsCannotCollect(String variant) {
        ready();
        switch (variant) {
            case "ONLINE" -> jdbc.update("UPDATE uteexpress.payments SET method='ONLINE' WHERE order_id=?", orderId);
            case "EXPIRED" -> jdbc.update("UPDATE uteexpress.payments SET expired_at=CURRENT_TIMESTAMP WHERE order_id=?", orderId);
            case "MULTIPLE" -> jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',107000,?)",
                    orderId, UUID.randomUUID().toString());
        }
        var before = state();
        rejects(() -> collect("107000"), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void historicalMissingPaymentStaysAbsentOnReadAndCollection() throws Exception {
        mvc.perform(get("/orders/{id}/payments", orderId).with(user(principal(buyer, "USER"))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        jdbc.update("UPDATE uteexpress.orders SET status='SHIPPING' WHERE id=?", orderId);
        var before = state();
        rejects(() -> collect("107000"), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        assertThat(paymentCount()).isZero();
    }

    @Test void missingOrderAndInvalidInternalCommandsFailWithoutMutation() {
        ready();
        var before = state();
        rejects(() -> transactions.execute(tx -> payments.collectCod(new CodCollectionCommand(Long.MAX_VALUE, money("107000")))),
                ErrorCode.RESOURCE_NOT_FOUND);
        rejects(() -> transactions.execute(tx -> payments.collectCod(null)), ErrorCode.INVALID_REQUEST);
        rejects(() -> transactions.execute(tx -> payments.collectCod(new CodCollectionCommand(0L, money("107000")))),
                ErrorCode.VALIDATION_FAILED);
        rejects(() -> transactions.execute(tx -> payments.initializeCodForNewOrder(null)), ErrorCode.VALIDATION_FAILED);
        rejects(() -> transactions.execute(tx -> payments.initializeCodForNewOrder(Long.MAX_VALUE)), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(state()).isEqualTo(before);
    }

    @Test void initializationCannotInventCodForNonNewOrder() {
        jdbc.update("UPDATE uteexpress.orders SET status='SHIPPING' WHERE id=?", orderId);
        var before = state();
        rejects(() -> initialize(), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void bothInternalMutationsRequireAnExistingTransaction() {
        assertThatThrownBy(() -> payments.initializeCodForNewOrder(orderId)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> payments.collectCod(new CodCollectionCommand(orderId, money("107000"))))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(paymentCount()).isZero();
    }

    @Test void failedCollectionRollsBackEnclosingFulfillmentEffects() {
        ready();
        var before = state();
        // Test-only fulfillment effect; no production delivery operation is introduced by PAY-01.
        rejects(() -> transactions.execute(tx -> {
            jdbc.update("UPDATE uteexpress.orders SET updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?", orderId);
            return payments.collectCod(new CodCollectionCommand(orderId, money("106999")));
        }), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void successfulCollectionJoinsFulfillmentTransactionAndOuterRollbackUndoesBoth() {
        ready();
        var before = state();
        transactions.executeWithoutResult(tx -> {
            assertThat(payments.collectCod(new CodCollectionCommand(orderId, money("107000"))).status()).isEqualTo(PaymentStatus.PAID);
            jdbc.update("UPDATE uteexpress.orders SET status='DELIVERED',delivered_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=?", orderId);
            assertThat(paymentRow()).containsEntry("status", "PAID");
            tx.setRollbackOnly();
        });
        assertThat(state()).isEqualTo(before);
        transactions.executeWithoutResult(tx -> {
            payments.collectCod(new CodCollectionCommand(orderId, money("107000")));
            jdbc.update("UPDATE uteexpress.orders SET status='DELIVERED',delivered_at=CURRENT_TIMESTAMP WHERE id=?", orderId);
        });
        assertThat(paymentRow()).containsEntry("status", "PAID");
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, orderId)).isEqualTo("DELIVERED");
    }

    @Test void concurrentDoubleCollectionHasOneSuccessAndOneConflict() throws Exception {
        ready();
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            @SuppressWarnings("unchecked") Future<Object>[] futures = new Future[2];
            transactions.executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, orderId);
                for (int i = 0; i < 2; i++) futures[i] = workers.submit(() -> {
                    try { return collect("107000"); }
                    catch (ApplicationException e) { return e.errorCode(); }
                });
                awaitBlockedCollectors(2);
            });
            var results = List.of(futures[0].get(15, TimeUnit.SECONDS), futures[1].get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(PaymentRecordView.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(ErrorCode.CONFLICT::equals)).hasSize(1);
        }
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(paymentRow()).containsEntry("status", "PAID");
    }

    @Test void staleManagedUnpaidEntityCannotReportAnotherSuccessfulCollection() throws Exception {
        ready();
        long paymentId = ((Number) paymentRow().get("id")).longValue();
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            rejects(() -> transactions.execute(tx -> {
                var stale = entityManager.find(Payment.class, paymentId);
                assertThat(stale.getStatus()).isEqualTo(PaymentStatus.UNPAID);
                try { worker.submit(() -> collect("107000")).get(15, TimeUnit.SECONDS); }
                catch (Exception e) { throw new AssertionError(e); }
                assertThat(stale.getStatus()).isEqualTo(PaymentStatus.UNPAID);
                return payments.collectCod(new CodCollectionCommand(orderId, money("107000")));
            }), ErrorCode.CONFLICT);
        }
        assertThat(paymentRow()).containsEntry("status", "PAID");
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test void databaseStillEnforcesOnePaidPerOrderAndPaymentForeignKey() {
        ready();
        collect("107000");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','PAID',107000,?)",
                orderId, UUID.randomUUID().toString())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',107000,?)",
                Long.MAX_VALUE, UUID.randomUUID().toString())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(paymentCount()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "VENDOR"})
    void ownerReadIsPersistedOnlyAndCannotForcePaidThroughQueryFields(String role) throws Exception {
        initialize();
        var before = state();
        mvc.perform(get("/orders/{id}/payments", orderId).with(user(principal(buyer, role)))
                        .param("buyerId", Long.toString(other)).param("status", "PAID").param("amount", "1")
                        .param("shipperId", Long.toString(other)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].method").value("COD"))
                .andExpect(jsonPath("$[0].status").value("UNPAID")).andExpect(jsonPath("$[0].amount").value(107000))
                .andExpect(jsonPath("$[0].attemptKey").doesNotExist()).andExpect(jsonPath("$[0].providerReference").doesNotExist());
        mvc.perform(get("/orders/{id}/payments", orderId).with(user(principal(buyer, role))).accept(MediaType.TEXT_HTML))
                .andExpect(redirectedUrl("/orders/" + orderId + "#payment-title"));
        mvc.perform(get("/orders/{id}", orderId).with(user(principal(buyer, role))).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk()).andExpect(content().string(containsString("UNPAID")));
        assertThat(state()).isEqualTo(before);
        jdbc.update("UPDATE uteexpress.orders SET status='SHIPPING' WHERE id=?", orderId);
        collect("107000");
        mvc.perform(get("/orders/{id}/payments", orderId).with(user(principal(buyer, role))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PAID"));
        mvc.perform(get("/orders/{id}", orderId).with(user(principal(buyer, role))).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payments[0].status").value("PAID"));
    }

    @ParameterizedTest @ValueSource(strings = {"application/json", "text/html"})
    void foreignAndMissingReadsAreIndistinguishableAndGuestsAreDenied(String type) throws Exception {
        initialize();
        var foreign = mvc.perform(get("/orders/{id}/payments", orderId).with(user(principal(other, "USER"))).accept(type))
                .andExpect(status().isNotFound()).andReturn();
        var missing = mvc.perform(get("/orders/{id}/payments", Long.MAX_VALUE).with(user(principal(other, "USER"))).accept(type))
                .andExpect(status().isNotFound()).andReturn();
        if (type.equals("application/json")) {
            assertThat(foreign.getResponse().getContentAsString()).contains("RESOURCE_NOT_FOUND").doesNotContain("107000", "COD");
            assertThat(missing.getResponse().getContentAsString()).contains("RESOURCE_NOT_FOUND").doesNotContain("107000", "COD");
        } else assertThat(foreign.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());
        mvc.perform(get("/orders/{id}/payments", orderId).accept(type)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(strings = {"SHIPPER", "ADMIN", "MANAGER"})
    void nonBuyerRoleCannotReadEvenIfItOwnsOrderOrSpoofsFields(String role) throws Exception {
        initialize();
        var before = state();
        for (MediaType type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            mvc.perform(get("/orders/{id}/payments", orderId).with(user(principal(buyer, role)))
                            .param("buyerId", Long.toString(buyer)).param("status", "PAID").accept(type))
                    .andExpect(status().isForbidden());
        }
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"abc", "9223372036854775808"})
    void malformedReadIdsUseControlledErrorsForJsonAndHtml(String id) throws Exception {
        for (MediaType type : List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_HTML)) {
            mvc.perform(get("/orders/{id}/payments", id).with(user(principal(buyer, "USER"))).accept(type))
                    .andExpect(status().isBadRequest());
        }
        assertThat(paymentCount()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "VENDOR", "SHIPPER", "ADMIN", "MANAGER"})
    void noRoleCanCollectOrForcePaidViaHttp(String role) throws Exception {
        initialize();
        var before = state();
        for (String method : List.of("POST", "PUT", "PATCH")) {
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), "/orders/{id}/payments", orderId)
                            .with(user(principal(buyer, role))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"PAID\",\"amount\":1,\"collectedAmount\":107000,\"shipperId\":" + buyer + "}"))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertThat(state()).isEqualTo(before);
    }

    private PaymentRecordView initialize() { return transactions.execute(tx -> payments.initializeCodForNewOrder(orderId)); }
    private void ready() {
        initialize();
        // Trusted fixture only. PAY-01 introduces no shipping/order transition endpoint.
        jdbc.update("UPDATE uteexpress.orders SET status='SHIPPING' WHERE id=?", orderId);
    }
    private PaymentRecordView collect(String amount) {
        return transactions.execute(tx -> payments.collectCod(new CodCollectionCommand(orderId, amount == null ? null : money(amount))));
    }
    private java.util.Map<String, Object> paymentRow() { return jdbc.queryForMap("SELECT * FROM uteexpress.payments WHERE order_id=?", orderId); }
    private Long paymentCount() { return jdbc.queryForObject("SELECT count(*) FROM uteexpress.payments WHERE order_id=?", Long.class, orderId); }
    private List<List<java.util.Map<String, Object>>> state() {
        return List.of(jdbc.queryForList("SELECT * FROM uteexpress.orders ORDER BY id"),
                jdbc.queryForList("SELECT * FROM uteexpress.payments ORDER BY id"),
                jdbc.queryForList("SELECT * FROM uteexpress.order_status_history ORDER BY id"));
    }
    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
    private long createUser() {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        return jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,'test-only-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, name + "@example.test", name + "@example.test", name, name);
    }
    private static UteExpressPrincipal principal(long id, String role) {
        return new UteExpressPrincipal(id, "opaque-" + id, null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true);
    }
    private static void rejects(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).errorCode()).isEqualTo(code));
    }
    private void awaitBlockedCollectors(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            Long waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%uteexpress.orders%'
                    """, Long.class);
            if (waiting >= expected) return;
            try { Thread.sleep(20); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        }
        throw new AssertionError("Both collectors must reach the PostgreSQL order row lock");
    }
}
