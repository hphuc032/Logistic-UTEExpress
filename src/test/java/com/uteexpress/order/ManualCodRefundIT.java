package com.uteexpress.order;

import com.uteexpress.common.exception.*;
import com.uteexpress.governance.dto.ManagedRole;
import com.uteexpress.governance.service.*;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.order.service.*;
import com.uteexpress.payment.dto.*;
import com.uteexpress.payment.service.ManualCodRefundService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
@Import(ManualCodRefundIT.Configuration.class)
@Timeout(50)
class ManualCodRefundIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    static final Instant AT = Instant.parse("2026-10-23T00:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager manager;
    @Autowired Events events;
    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired RoleGovernanceService roles;
    @Autowired AccountGovernanceService governance;
    @MockitoSpyBean RefundOrderAuthority authority;
    @MockitoSpyBean ManualCodRefundService payments;
    @MockitoSpyBean OrderRepository orders;
    @MockitoSpyBean OrderStatusHistoryRepository history;
    @MockitoSpyBean AuditLogService audit;
    RefundOrderAuthority authoritySpy;
    ManualCodRefundService paymentSpy;
    AuditLogService auditSpy;
    TransactionTemplate tx;
    long admin, otherAdmin, buyer, vendor, shop, product, order, request;
    String key;

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        authoritySpy = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(authority);
        paymentSpy = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(payments);
        auditSpy = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
        admin = createUser("ADMIN"); otherAdmin = createUser("ADMIN"); buyer = createUser("USER"); vendor = createUser("VENDOR");
        shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, vendor, UUID.randomUUID().toString());
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id", Long.class, UUID.randomUUID().toString());
        product = jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',100000,8,'ACTIVE') RETURNING id", Long.class, shop, category);
        order = receivedOrder();
        request = jdbc.queryForObject("SELECT id FROM uteexpress.return_requests WHERE order_id=?", Long.class, order);
        key = UUID.randomUUID().toString();
        auth(admin, "ADMIN"); events.values.clear();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void successAndReplayUseOriginalEvidenceAndPreserveOtherModules() {
        doAnswer(i -> {
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("read committed");
            return i.callRealMethod();
        }).when(paymentSpy).lockKey(any());
        var before = sideEffects();
        var result = lifecycle.completeRefund(command());
        assertThat(result.status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(result.refundAmount()).isEqualByComparingTo("105000");
        assertThat(result.proofReference()).isEqualTo("PROOF");
        assertThat(result.serverTimestamp()).isEqualTo(AT);
        assertThat(result.replay()).isFalse();
        assertThat(sideEffects()).isEqualTo(before);
        assertEffects(1);
        var committed = effects();
        var replay = lifecycle.completeRefund(new ManualRefundCommand(order, 2L, 2L, "PROOF", key));
        assertThat(replay).isEqualTo(new RefundResult(result.refundId(), order, result.refundAmount(), result.status(), result.proofReference(), result.serverTimestamp(), true));
        assertThat(effects()).usingRecursiveComparison().isEqualTo(committed);
        assertThat(events.values).hasSize(1);
    }

    @Test void fullRefundPreservesDeliveredShipmentRedeemedVoucherAndInventory() {
        long shipper = createUser("SHIPPER");
        String providerCode = "P" + UUID.randomUUID().toString().replace("-", "").substring(0,18).toUpperCase();
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id", Long.class, providerCode);
        jdbc.update("""
                INSERT INTO uteexpress.shipments(order_id,provider_id,assigned_shipper_id,status,shipping_service_snapshot,
                    fee_snapshot,attempt_count,picked_up_at,delivered_at)
                VALUES (?,?,?,'DELIVERED','STANDARD',15000,1,?,?)
                """, order, provider, shipper, java.sql.Timestamp.from(AT.minusSeconds(120)), java.sql.Timestamp.from(AT.minusSeconds(60)));
        String code = "V" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        long voucher = jdbc.queryForObject("""
                INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by)
                VALUES (?,'SHOP','FIXED',?,10000,0,?,?,100,2,?) RETURNING id
                """, Long.class, code, shop, java.sql.Timestamp.from(AT.minusSeconds(3600)), java.sql.Timestamp.from(AT.plusSeconds(3600)), vendor);
        jdbc.update("UPDATE uteexpress.orders SET voucher_id=?,voucher_code=?,voucher_scope='SHOP',shipping_provider_id=?,shipping_service_code='STANDARD' WHERE id=?", voucher, code, provider, order);
        jdbc.update("INSERT INTO uteexpress.voucher_usages(voucher_id,user_id,order_id,status,discount_amount) VALUES (?,?,?,'REDEEMED',10000)", voucher, buyer, order);
        var before = sideEffects();
        var orderBefore = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order);
        var result = lifecycle.completeRefund(command());
        assertThat(result.refundAmount()).isEqualByComparingTo("105000");
        assertThat(sideEffects()).isEqualTo(before);
        var orderAfter = jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order);
        for (var mutable : List.of("status", "version", "updated_at")) { orderBefore.remove(mutable); orderAfter.remove(mutable); }
        assertThat(orderAfter).isEqualTo(orderBefore);
        assertEffects(1);
    }

    @ParameterizedTest @ValueSource(strings = {"actor", "proof", "orderVersion", "returnVersion", "order", "key"})
    void committedCollisionsConflict(String kind) {
        lifecycle.completeRefund(command()); var before = effects();
        var c = command();
        switch (kind) {
            case "actor" -> auth(otherAdmin, "ADMIN");
            case "proof" -> c = new ManualRefundCommand(order, 2L, 2L, "DIFFERENT", key);
            case "orderVersion" -> c = new ManualRefundCommand(order, 3L, 2L, "PROOF", key);
            case "returnVersion" -> c = new ManualRefundCommand(order, 2L, 1L, "PROOF", key);
            case "order" -> c = new ManualRefundCommand(receivedOrder(), 2L, 2L, "PROOF", key);
            case "key" -> c = new ManualRefundCommand(order, 2L, 2L, "PROOF", UUID.randomUUID().toString());
        }
        var finalCommand = c; auth(kind.equals("actor") ? otherAdmin : admin, "ADMIN");
        rejects(() -> lifecycle.completeRefund(finalCommand), ErrorCode.CONFLICT);
        assertThat(effects()).usingRecursiveComparison().isEqualTo(before);
    }

    @ParameterizedTest @CsvSource({"0,2", "1,2", "3,2", "2,0", "2,1", "2,3"})
    void staleOriginalVersionsLeaveNoKey(long ov, long rv) {
        rejects(() -> lifecycle.completeRefund(new ManualRefundCommand(order, ov, rv, "PROOF", key)), ErrorCode.CONFLICT);
        assertEffects(0);
        assertThat(lifecycle.completeRefund(command()).replay()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"status", "paymentMissing", "online", "unpaid", "paidAt", "expired", "amount", "zero", "fraction", "ambiguous", "receiptHistory", "requestStatus"})
    void invalidPersistedBusinessFactsFailClosed(String kind) {
        switch (kind) {
            case "status" -> jdbc.update("UPDATE uteexpress.orders SET status='DELIVERED' WHERE id=?", order);
            case "paymentMissing" -> jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?", order);
            case "online" -> jdbc.update("UPDATE uteexpress.payments SET method='ONLINE' WHERE order_id=?", order);
            case "unpaid" -> jdbc.update("UPDATE uteexpress.payments SET status='UNPAID' WHERE order_id=?", order);
            case "paidAt" -> jdbc.update("UPDATE uteexpress.payments SET paid_at=NULL WHERE order_id=?", order);
            case "expired" -> jdbc.update("UPDATE uteexpress.payments SET expired_at=paid_at WHERE order_id=?", order);
            case "amount" -> jdbc.update("UPDATE uteexpress.payments SET amount=104999 WHERE order_id=?", order);
            case "zero" -> jdbc.update("UPDATE uteexpress.payments SET amount=0 WHERE order_id=?", order);
            case "fraction" -> jdbc.update("UPDATE uteexpress.payments SET amount=105000.01 WHERE order_id=?", order);
            case "ambiguous" -> jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',105000,?)", order, UUID.randomUUID().toString());
            case "receiptHistory" -> jdbc.update("DELETE FROM uteexpress.order_status_history WHERE order_id=? AND to_status='RETURNED'", order);
            case "requestStatus" -> { // An approved row without receipt cannot authorize a refund.
                long other = deliveredOrder();
                jdbc.update("UPDATE uteexpress.orders SET status='RETURNED' WHERE id=?", other);
                order = other;
            }
        }
        var before = effects();
        rejects(() -> lifecycle.completeRefund(command()), ErrorCode.CONFLICT);
        assertThat(effects()).usingRecursiveComparison().isEqualTo(before);
        assertThat(count("refunds")).isZero(); assertThat(events.values).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"ROLE", "LOCKED"})
    void replayRequiresCurrentPersistedAuthorization(String restriction) {
        lifecycle.completeRefund(command()); var before = effects();
        auth(otherAdmin, "ADMIN");
        if (restriction.equals("ROLE")) roles.change(admin, 0L, ManagedRole.ADMIN, false);
        else governance.setLocked(admin, 0L, true);
        auth(admin, "ADMIN");
        rejects(() -> lifecycle.completeRefund(command()), ErrorCode.ACCESS_DENIED);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.refunds WHERE order_id=?", order)).usingRecursiveComparison().isEqualTo(before.get(0));
        assertThat(events.values).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "VENDOR", "MANAGER", "SHIPPER", "ADMIN"})
    void apiAuthorizationUsesPrincipalAndPersistedIdentity(String role) throws Exception {
        long actor = role.equals("ADMIN") ? admin : createUser(role);
        var req = post(url()).with(user(principal(actor, role))).with(csrf()).accept("application/json")
                .contentType("application/json").header("Idempotency-Key", key).content(body());
        if (role.equals("ADMIN")) mvc.perform(req).andExpect(status().isOk()).andExpect(jsonPath("status").value("SUCCEEDED"));
        else mvc.perform(req).andExpect(status().isForbidden());
    }

    @Test void apiRejectsAnonymousCsrfMissingKeyDuplicateKeyForgedFieldsAndQueryActor() throws Exception {
        mvc.perform(post(url()).with(anonymous()).with(csrf()).accept("application/json").contentType("application/json").content(body()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(url()).with(user(principal(admin, "ADMIN"))).accept("application/json").contentType("application/json").header("Idempotency-Key", key).content(body()))
                .andExpect(status().isForbidden());
        mvc.perform(post(url()).with(user(principal(admin, "ADMIN"))).with(csrf()).contentType("application/json").content(body()))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url()).with(user(principal(admin, "ADMIN"))).with(csrf()).contentType("application/json").header("Idempotency-Key", key, "second").content(body()))
                .andExpect(status().isBadRequest());
        for (String field : List.of("paymentId", "amount", "status", "actor"))
            mvc.perform(post(url()).with(user(principal(admin, "ADMIN"))).with(csrf()).contentType("application/json").header("Idempotency-Key", key)
                    .content(body().replace("}", ",\"" + field + "\":1}"))).andExpect(status().isBadRequest());
        mvc.perform(post(url()).param("actor", "1").with(user(principal(admin, "ADMIN"))).with(csrf()).contentType("application/json").header("Idempotency-Key", key).content(body()))
                .andExpect(status().isBadRequest());
        assertEffects(0);
    }

    @Test void apiRejectsForgedAdminAndInactiveAdmin() throws Exception {
        mvc.perform(post(url()).with(user(principal(buyer, "ADMIN"))).with(csrf()).contentType("application/json").header("Idempotency-Key", key).content(body()))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", admin);
        mvc.perform(post(url()).with(user(principal(admin, "ADMIN"))).with(csrf()).contentType("application/json").header("Idempotency-Key", key).content(body()))
                .andExpect(status().isForbidden());
        assertEffects(0);
    }

    @Test void outerTransactionsAndInternalStandaloneCallsAreRejected() {
        tx.executeWithoutResult(s -> rejects(() -> lifecycle.completeRefund(command()), ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> payments.lockKey(com.uteexpress.payment.service.ManualRefundPayload.from(command())))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> authority.authorizeAdmin()).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertEffects(0);
    }

    @ParameterizedTest @ValueSource(strings = {"ledger", "order", "history", "audit", "beforeCommit"})
    void everyFlushedStageRollsBackAndDoesNotReserveKey(String stage) {
        var before = effects(); var side = sideEffects();
        org.mockito.stubbing.Answer<Object> failure = invocation -> { invocation.callRealMethod(); throw new InjectedFailure(); };
        switch (stage) {
            case "ledger" -> doAnswer(failure).when(paymentSpy).record(any(), any(), anyLong(), anyLong(), any(), any());
            case "order" -> doAnswer(i -> { entities.flush(); throw new InjectedFailure(); }).when(orders).saveAndFlush(any());
            case "history" -> doAnswer(i -> { entities.persist(i.getArgument(0)); entities.flush(); throw new InjectedFailure(); }).when(history).saveAndFlush(any());
            case "audit" -> doAnswer(failure).when(auditSpy).append(any());
            case "beforeCommit" -> doAnswer(i -> {
                Object result = i.callRealMethod();
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) { throw new InjectedFailure(); }
                    });
                return result;
            }).when(auditSpy).append(any());
        }
        assertThatThrownBy(() -> lifecycle.completeRefund(command())).isInstanceOf(InjectedFailure.class);
        assertThat(effects()).usingRecursiveComparison().isEqualTo(before); assertThat(sideEffects()).isEqualTo(side); assertThat(events.values).isEmpty();
        reset(paymentSpy, orders, history, auditSpy);
        assertThat(lifecycle.completeRefund(command()).replay()).isFalse(); assertEffects(1);
    }

    @ParameterizedTest @ValueSource(strings = {"replay", "rollback", "actor", "proof", "order", "differentKey", "differentKeyRollback"})
    void concurrentRefundsShowRealAdvisoryOrOrderWaitAndCommittedEffects(String schedule) throws Exception {
        long foreign = schedule.equals("order") ? receivedOrder() : order;
        auth(admin, "ADMIN"); events.values.clear();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pids = new ConcurrentHashMap<String,Integer>(); var first = new AtomicBoolean(true);
        doAnswer(i -> { pids.put(Thread.currentThread().getName(), pid()); return i.callRealMethod(); }).when(paymentSpy).lockKey(any());
        doAnswer(i -> {
            Object result = i.callRealMethod();
            if (first.getAndSet(false)) { entered.countDown(); await(release); if (schedule.equals("rollback") || schedule.equals("differentKeyRollback")) throw new InjectedFailure(); }
            return result;
        }).when(paymentSpy).record(any(), any(), anyLong(), anyLong(), any(), any());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var winner = pool.submit(() -> worker("refund-first", admin, command()));
            await(entered); assertThat(events.values).isEmpty(); assertEffects(0);
            var secondCommand = new ManualRefundCommand(foreign, 2L, 2L, schedule.equals("proof") ? "OTHER" : "PROOF",
                    schedule.startsWith("differentKey") ? UUID.randomUUID().toString() : key);
            var waiter = pool.submit(() -> worker("refund-second", schedule.equals("actor") ? otherAdmin : admin, secondCommand));
            awaitPid(pids, "refund-second");
            blocked(pids.get("refund-second"), pids.get("refund-first"), schedule.startsWith("differentKey") ? "orders" : "pg_advisory_xact_lock");
            release.countDown();
            Object a = winner.get(15, TimeUnit.SECONDS), b = waiter.get(15, TimeUnit.SECONDS);
            if (schedule.equals("rollback") || schedule.equals("differentKeyRollback")) { assertThat(a).isInstanceOf(InjectedFailure.class); assertThat(b).isInstanceOfSatisfying(RefundResult.class, r -> assertThat(r.replay()).isFalse()); }
            else {
                assertThat(a).isInstanceOf(RefundResult.class);
                if (schedule.equals("replay")) assertThat(b).isInstanceOfSatisfying(RefundResult.class, r -> assertThat(r.replay()).isTrue());
                else assertThat(b).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
            }
            assertEffects(1); assertThat(events.values).hasSize(1);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest @CsvSource({"refundFirst,role,false", "revocationFirst,role,false", "refundFirst,status,false", "revocationFirst,status,false",
            "refundFirst,role,true", "revocationFirst,role,true", "refundFirst,status,true", "revocationFirst,status,true"})
    void adminRevocationRacesRetainAuthorityThroughCommit(String schedule, String mutation, boolean replay) throws Exception {
        if (replay) lifecycle.completeRefund(command());
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pids = new ConcurrentHashMap<String,Integer>();
        if (schedule.equals("refundFirst")) doAnswer(i -> {
            Object result = i.callRealMethod(); pids.put("refund", pid()); held.countDown(); await(release); return result;
        }).when(authoritySpy).authorizeAdmin();
        else doAnswer(i -> { pids.put("refund", pid()); return i.callRealMethod(); }).when(authoritySpy).authorizeAdmin();
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Object> refund = () -> worker("refund", admin, command());
            java.util.concurrent.Callable<Object> revoke = () -> {
                auth(otherAdmin, "ADMIN");
                try {
                    tx.executeWithoutResult(s -> {
                        pids.put("revoke", pid());
                        if (mutation.equals("role")) roles.change(admin, 0L, ManagedRole.ADMIN, false);
                        else governance.setLocked(admin, 0L, true);
                        if (schedule.equals("revocationFirst")) { held.countDown(); await(release); }
                    }); return "revoked";
                } finally { SecurityContextHolder.clearContext(); }
            };
            var a = pool.submit(schedule.equals("refundFirst") ? refund : revoke); await(held);
            var b = pool.submit(schedule.equals("refundFirst") ? revoke : refund);
            String waiter = schedule.equals("refundFirst") ? "revoke" : "refund";
            String holder = schedule.equals("refundFirst") ? "refund" : "revoke";
            awaitPid(pids, waiter); blocked(pids.get(waiter), pids.get(holder), "users"); release.countDown();
            Object firstResult = a.get(15, TimeUnit.SECONDS), secondResult = b.get(15, TimeUnit.SECONDS);
            Object refundResult = schedule.equals("refundFirst") ? firstResult : secondResult;
            if (schedule.equals("refundFirst")) { assertThat(refundResult).isInstanceOf(RefundResult.class); assertEffects(1); }
            else { assertThat(refundResult).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED)); assertEffects(replay ? 1 : 0); }
            assertThat(events.values).hasSize(replay || schedule.equals("refundFirst") ? 1 : 0);
        } finally { release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest @CsvSource({"refunds,refundFirst", "refunds,fkFirst", "order_status_history,refundFirst", "order_status_history,fkFirst", "audit_logs,refundFirst", "audit_logs,fkFirst"})
    void accountFkLocksCoexistWithRefundAndBlockRevocationUntilBothCommit(String table, String schedule) throws Exception {
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var fkHeld = new CountDownLatch(1); var releaseFk = new CountDownLatch(1);
        var pids = new ConcurrentHashMap<String,Integer>();
        doAnswer(i -> { Object result = i.callRealMethod(); pids.put("refund", pid()); held.countDown(); await(release); return result; }).when(authoritySpy).authorizeAdmin();
        long foreign = receivedOrder(); auth(admin, "ADMIN"); events.values.clear();
        var pool = Executors.newFixedThreadPool(3);
        try {
            java.util.concurrent.Callable<Boolean> insertFk = () -> tx.execute(s -> {
                pids.put("fk", pid()); jdbc.execute("SET LOCAL lock_timeout='12s'");
                // Real inserts take Account KEY SHARE. Hibernate's Account NO KEY UPDATE is compatible.
                if (table.equals("order_status_history")) jdbc.update("INSERT INTO uteexpress.order_status_history(order_id,to_status,actor_id) VALUES (?,'DELIVERED',?)", foreign, admin);
                else if (table.equals("audit_logs")) jdbc.update("INSERT INTO uteexpress.audit_logs(actor_id,action,target_type,target_id,before_summary,after_summary,reason) VALUES (?,'TEST','ORDER',?,'','','TEST')", admin, foreign);
                else jdbc.update("""
                        INSERT INTO uteexpress.refunds(order_id,payment_id,return_request_id,processed_by,operation,
                            idempotency_key,schema_version,expected_order_version,expected_return_request_version,
                            canonical_payload,payload_sha256,amount,status,proof_reference,server_timestamp,created_at,
                            result_order_version,payment_paid_at,return_received_at)
                        SELECT p.order_id,p.id,r.id,?,'PAY02_MANUAL_COD_REFUND_V1',?,1,2,2,
                            decode('01','hex'),decode(repeat('00',32),'hex'),105000,'SUCCEEDED','OTHER',CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP,3,p.paid_at,r.received_at
                        FROM uteexpress.payments p JOIN uteexpress.return_requests r ON p.order_id=r.order_id WHERE p.order_id=?
                        """, admin, UUID.randomUUID().toString(), foreign);
                fkHeld.countDown(); await(releaseFk); return true;
            });
            Future<Object> refund;
            Future<Boolean> fk;
            if (schedule.equals("refundFirst")) {
                refund = pool.submit(() -> worker("refund", admin, command())); await(held);
                fk = pool.submit(insertFk); await(fkHeld);
            } else {
                fk = pool.submit(insertFk); await(fkHeld);
                refund = pool.submit(() -> worker("refund", admin, command())); await(held);
            }
            assertThat(events.values).isEmpty(); assertEffects(0);
            var revoke = pool.submit(() -> {
                auth(otherAdmin, "ADMIN");
                try { tx.executeWithoutResult(s -> { pids.put("revoke", pid()); roles.change(admin, 0L, ManagedRole.ADMIN, false); }); return true; }
                finally { SecurityContextHolder.clearContext(); }
            });
            awaitPid(pids, "revoke");
            if (schedule.equals("refundFirst")) {
                blocked(pids.get("revoke"), pids.get("refund"), "users");
                release.countDown(); assertThat(refund.get(15, TimeUnit.SECONDS)).isInstanceOf(RefundResult.class);
                blocked(pids.get("revoke"), pids.get("fk"), "users");
                releaseFk.countDown(); assertThat(fk.get(15, TimeUnit.SECONDS)).isTrue();
            } else {
                blocked(pids.get("revoke"), pids.get("fk"), "users");
                releaseFk.countDown(); assertThat(fk.get(15, TimeUnit.SECONDS)).isTrue();
                blocked(pids.get("revoke"), pids.get("refund"), "users");
                release.countDown(); assertThat(refund.get(15, TimeUnit.SECONDS)).isInstanceOf(RefundResult.class);
            }
            assertThat(revoke.get(15, TimeUnit.SECONDS)).isTrue();
            assertEffects(1);
        } finally { release.countDown(); releaseFk.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue(); }
    }

    private Object worker(String name, long actor, ManualRefundCommand command) {
        Thread.currentThread().setName(name); auth(actor, "ADMIN");
        try { return lifecycle.completeRefund(command); } catch (RuntimeException e) { return e; }
        finally { SecurityContextHolder.clearContext(); }
    }
    private int pid() { return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class); }
    private void blocked(int waiter, int holder, String sql) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            var activity = jdbc.queryForMap("SELECT wait_event_type,query FROM pg_stat_activity WHERE pid=?", waiter);
            if ("Lock".equals(activity.get("wait_event_type")) && jdbc.queryForList("SELECT unnest(pg_blocking_pids(?))", Integer.class, waiter).contains(holder)) {
                assertThat(activity.get("query").toString().toLowerCase(Locale.ROOT)).contains(sql);
                System.out.println("PAY02 blocking: waiter=" + waiter + " holder=" + holder + " SQL=" + sql);
                return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("Missing real PostgreSQL blocking: " + waiter + " -> " + holder + " " + sql);
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private static void awaitPid(Map<String,Integer> pids, String name) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!pids.containsKey(name) && System.nanoTime() < end) Thread.onSpinWait();
        assertThat(pids).containsKey(name);
    }
    private void assertEffects(int expected) {
        assertThat(count("refunds")).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order)).isEqualTo(expected == 1 ? "REFUNDED" : "RETURNED");
        assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.orders WHERE id=?", Long.class, order)).isEqualTo(2L + expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=? AND to_status='REFUNDED'", Long.class, order)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_id=? AND action='ORDER_COD_REFUNDED'", Long.class, order)).isEqualTo(expected);
    }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM uteexpress." + table + " WHERE order_id=?", Long.class, order); }
    private List<Object> effects() { return List.of(jdbc.queryForList("SELECT * FROM uteexpress.refunds WHERE order_id=?", order),
            jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE id=?", order),
            jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", order),
            jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_id=? AND target_type='ORDER' ORDER BY id", order)); }
    private List<Object> sideEffects() { return List.of(jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?", order),
            jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order),
            jdbc.queryForList("SELECT * FROM uteexpress.products WHERE id=?", product),
            jdbc.queryForList("SELECT * FROM uteexpress.shipments ORDER BY id"),
            jdbc.queryForList("SELECT * FROM uteexpress.shipment_delivery_failures ORDER BY id"),
            jdbc.queryForList("SELECT * FROM uteexpress.vouchers ORDER BY id"),
            jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages ORDER BY id")); }
    private long receivedOrder() {
        long id = deliveredOrder();
        auth(buyer, "USER"); lifecycle.requestReturn(new RequestReturnCommand(id, 0L, "Reason"));
        auth(vendor, "VENDOR"); lifecycle.approveReturn(new ReturnDecisionCommand(id, 1L, 0L, null));
        lifecycle.receiveReturn(new ReceiveReturnCommand(id, 1L, 1L, "RECEIPT"));
        return id;
    }
    private long deliveredOrder() {
        String code = UUID.randomUUID().toString();
        long id = jdbc.queryForObject("""
                INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,
                    receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,
                    commission_amount,commission_rate_snapshot,delivered_at)
                VALUES (?,?,'hash',?,?,'DELIVERED','Receiver','0900000000','VN','District','Detail',100000,10000,15000,105000,0,0,?) RETURNING id
                """, Long.class, code, code, buyer, shop, java.sql.Timestamp.from(AT.minusSeconds(60)));
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key,paid_at) VALUES (?,'COD','PAID',105000,?,?)", id, code, java.sql.Timestamp.from(AT.minusSeconds(60)));
        return id;
    }
    private long createUser(String role) {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0,20);
        long id = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,'test','ACTIVE',CURRENT_TIMESTAMP) RETURNING id", Long.class, name+"@test.invalid", name+"@test.invalid", name, name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role);
        return id;
    }
    private ManualRefundCommand command() { return new ManualRefundCommand(order, 2L, 2L, "  PROOF  ", key); }
    private String url() { return "/admin/orders/" + order + "/refunds"; }
    private String body() { return "{\"expectedOrderVersion\":2,\"expectedReturnRequestVersion\":2,\"proofReference\":\"PROOF\"}"; }
    private static UteExpressPrincipal principal(long actor, String role) { return new UteExpressPrincipal(actor, "opaque", null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true); }
    private static void auth(long actor, String role) { var p = principal(actor, role); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities())); }
    private static void rejects(Runnable action, ErrorCode code) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(code)); }
    static class InjectedFailure extends RuntimeException { }
    @TestConfiguration static class Configuration {
        @Bean @Primary Clock refundClock() { return Clock.fixed(AT, ZoneOffset.UTC); }
        @Bean Events refundEvents() { return new Events(); }
    }
    static class Events {
        @Autowired javax.sql.DataSource dataSource;
        final List<OrderStatusChangedEvent> values = new CopyOnWriteArrayList<>();
        @EventListener public void onEvent(OrderStatusChangedEvent event) {
            if (event.toStatus() == OrderStatus.REFUNDED) {
                // A separate physical connection cannot see uncommitted lifecycle writes.
                try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
                        SELECT count(*) FROM uteexpress.refunds f JOIN uteexpress.orders o ON o.id=f.order_id
                        WHERE o.id=? AND o.status='REFUNDED'
                            AND EXISTS (SELECT 1 FROM uteexpress.order_status_history h WHERE h.order_id=o.id AND h.to_status='REFUNDED')
                            AND EXISTS (SELECT 1 FROM uteexpress.audit_logs a WHERE a.target_id=o.id AND a.action='ORDER_COD_REFUNDED')
                        """)) {
                    statement.setLong(1, event.orderId());
                    try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); assertThat(result.getLong(1)).isEqualTo(1); }
                } catch (java.sql.SQLException e) { throw new AssertionError(e); }
            }
            values.add(event);
        }
    }
}
