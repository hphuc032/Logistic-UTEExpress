package com.uteexpress.order;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.dto.RequestReturnCommand;
import com.uteexpress.order.dto.ReturnDecisionCommand;
import com.uteexpress.order.dto.ReviewableOrderItem;
import com.uteexpress.order.service.*;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @Testcontainers @Import(OrderEligibilityIT.Configuration.class)
class OrderEligibilityIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderEligibilityQueryService query;
    @Autowired OrderReviewWriteBoundary boundary;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired AccountIdentityService identities;
    @MockitoSpyBean PlatformTransactionManager manager;
    @Autowired JoinedWriter writer;
    @MockitoSpyBean ReturnOrderAuthority returns;
    long buyer, otherBuyer, vendor, product, order, item, foreignItem;

    @BeforeEach void fixture() {
        buyer = user("USER"); otherBuyer = user("USER"); vendor = user("VENDOR");
        long shop = jdbc.queryForObject("""
                INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status)
                VALUES (?,'Review shop',?,'Pickup','APPROVED') RETURNING id
                """, Long.class, vendor, UUID.randomUUID().toString());
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Review',?,true) RETURNING id",
                Long.class, UUID.randomUUID().toString());
        product = jdbc.queryForObject("""
                INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status)
                VALUES (?,?,'Review product',100,10,'ACTIVE') RETURNING id
                """, Long.class, shop, category);
        order = deliveredOrder(buyer, shop);
        item = item(order);
        foreignItem = item(deliveredOrder(otherBuyer, shop));
        // Persisted DELIVERED fixtures; tested transitions below use the real Return service.
        auth(buyer);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @EnumSource(OrderStatus.class)
    void queryAndWriteRequireExactlyCurrentDeliveredState(OrderStatus status) {
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?", status.name(), order);
        var before = state();
        assertThat(query.isCurrentBuyerEligibleToReview(item)).isEqualTo(status == OrderStatus.DELIVERED);
        var called = new AtomicBoolean();
        Runnable write = () -> boundary.withEligibleCurrentBuyerItem(item, facts -> { called.set(true); return facts; });
        if (status == OrderStatus.DELIVERED) write.run(); else fails(ErrorCode.CONFLICT, write);
        assertThat(called.get()).isEqualTo(status == OrderStatus.DELIVERED);
        assertThat(state()).isEqualTo(before);
    }

    @Test void missingAndForeignItemsAreIndistinguishableAndCannotCallWriter() {
        var before = state();
        for (Long selected : Arrays.asList(foreignItem, Long.MAX_VALUE, null, 0L, -1L)) {
            assertThat(query.isCurrentBuyerEligibleToReview(selected)).isFalse();
            fails(ErrorCode.RESOURCE_NOT_FOUND, () -> boundary.withEligibleCurrentBuyerItem(selected,
                    facts -> { throw new AssertionError("Unavailable item must never invoke writer"); }));
        }
        auth(otherBuyer);
        assertThat(query.isCurrentBuyerEligibleToReview(item)).isFalse();
        assertThat(query.isCurrentBuyerEligibleToReview(foreignItem)).isTrue();
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"none", "anonymous", "untrustedPrincipal"})
    void unauthenticatedOrUntrustedPrincipalUsesSecurityConvention(String mode) {
        SecurityContextHolder.clearContext();
        if (mode.equals("anonymous")) SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("test", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        if (mode.equals("untrustedPrincipal")) SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(Long.toString(buyer), null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        fails(ErrorCode.UNAUTHENTICATED, () -> query.isCurrentBuyerEligibleToReview(item));
        fails(ErrorCode.UNAUTHENTICATED, () -> boundary.withEligibleCurrentBuyerItem(item, facts -> true));
    }

    @ParameterizedTest @ValueSource(strings = {"LOCKED", "ADMIN", "VENDOR"})
    void persistedEligibilityOverridesStalePrincipal(String eligibility) {
        if (eligibility.equals("LOCKED")) jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", buyer);
        else {
            jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?", buyer);
            jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", buyer, eligibility);
        }
        if (eligibility.equals("VENDOR")) {
            assertThat(query.isCurrentBuyerEligibleToReview(item)).isTrue();
            assertThat(boundary.withEligibleCurrentBuyerItem(item, ReviewableOrderItem::buyerId)).isEqualTo(buyer);
        } else {
            fails(ErrorCode.ACCESS_DENIED, () -> query.isCurrentBuyerEligibleToReview(item));
            fails(ErrorCode.ACCESS_DENIED, () -> boundary.withEligibleCurrentBuyerItem(item, facts -> true));
        }
    }

    @Test void trustedFactsAndJoinedWriterShareOneWritableTransactionThroughCommit() {
        var committed = new AtomicBoolean();
        String result = boundary.withEligibleCurrentBuyerItem(item, facts -> {
            assertThat(facts).isEqualTo(new ReviewableOrderItem(item, order, buyer, product));
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            int pid = pid();
            assertThat(writer.write(facts.buyerId())).isEqualTo(pid);
            afterCommit(() -> committed.set(true));
            assertThat(committed).isFalse();
            return "created by callback";
        });
        assertThat(result).isEqualTo("created by callback");
        assertThat(committed).isTrue();
        assertThat(marker()).isEqualTo("joined callback");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void callbackAndBeforeCommitFailuresRollBackJoinedWrites(boolean beforeCommit) {
        var committed = new AtomicBoolean();
        assertThatThrownBy(() -> boundary.withEligibleCurrentBuyerItem(item, facts -> {
            writer.write(facts.buyerId());
            afterCommit(() -> committed.set(true));
            if (beforeCommit) {
                beforeCommit(() -> { throw new IllegalStateException("callback rollback"); });
                return true;
            }
            throw new IllegalStateException("callback rollback");
        })).isInstanceOf(IllegalStateException.class).hasMessage("callback rollback");
        assertThat(marker()).isNull();
        assertThat(committed).isFalse();
        // Locks have been released: the actual Return write can now complete.
        lifecycle.requestReturn(request());
        assertReturnCommitted();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void existingTransactionsAreRejectedIncludingAccountHeldAndReadOnlyCallers(boolean readOnly) {
        var tx = new TransactionTemplate(manager); tx.setReadOnly(readOnly);
        tx.executeWithoutResult(s -> {
            if (!readOnly) identities.requireActiveAccountForUpdate(buyer);
            fails(ErrorCode.INVALID_REQUEST, () -> boundary.withEligibleCurrentBuyerItem(item,
                    facts -> { throw new AssertionError("No callback inside outer transaction"); }));
        });
    }

    @Test void advisoryTrueDoesNotAuthorizeWriteAfterActualReturnCommit() {
        assertThat(query.isCurrentBuyerEligibleToReview(item)).isTrue();
        lifecycle.requestReturn(request());
        assertThat(query.isCurrentBuyerEligibleToReview(item)).isFalse();
        fails(ErrorCode.CONFLICT, () -> boundary.withEligibleCurrentBuyerItem(item, facts -> writer.write(facts.buyerId())));
        assertThat(marker()).isNull();
    }

    @Test void rejectionRestoresDeliveredEligibilityWithoutInventingReviewVisibilityPolicy() {
        lifecycle.requestReturn(request());
        auth(vendor, "VENDOR");
        lifecycle.rejectReturn(new ReturnDecisionCommand(order, 1L, 0L, "Rejected"));
        auth(buyer);
        assertThat(query.isCurrentBuyerEligibleToReview(item)).isTrue();
        assertThat(boundary.withEligibleCurrentBuyerItem(item, ReviewableOrderItem::orderId)).isEqualTo(order);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) @Timeout(45)
    void orderLockSerializesBoundaryAndActualReturnInBothSchedules(boolean returnFirst) throws Exception {
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var holderPid = new AtomicInteger(); var waiterPid = new AtomicInteger();
        var callbackCommitted = new AtomicBoolean(); var callbackCalled = new AtomicBoolean();
        observeTransactionPid(waiterPid, "eligibility-waiter");
        if (returnFirst) {
            var returnSpy = AopTestUtils.<ReturnOrderAuthority>getUltimateTargetObject(returns);
            doAnswer(call -> {
                Object result = call.callRealMethod();
                holderPid.set(pid());
                beforeCommit(() -> { held.countDown(); await(release); });
                return result;
            }).when(returnSpy).create(any(), anyString(), any());
        }
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> holder = pool.submit(() -> {
                Thread.currentThread().setName("eligibility-holder"); auth(buyer);
                try {
                    if (returnFirst) lifecycle.requestReturn(request());
                    else boundary.withEligibleCurrentBuyerItem(item, facts -> {
                        callbackCalled.set(true); holderPid.set(pid()); writer.write(facts.buyerId());
                        afterCommit(() -> callbackCommitted.set(true));
                        // Callback has returned when beforeCommit holds the Order lock.
                        beforeCommit(() -> { held.countDown(); await(release); });
                        return true;
                    });
                    return "OK";
                } finally { SecurityContextHolder.clearContext(); }
            });
            await(held);
            Future<String> waiter = pool.submit(() -> {
                Thread.currentThread().setName("eligibility-waiter"); auth(buyer);
                try {
                    if (!returnFirst) lifecycle.requestReturn(request());
                    else boundary.withEligibleCurrentBuyerItem(item, facts -> { callbackCalled.set(true); return true; });
                    return "OK";
                } catch (ApplicationException error) { return error.errorCode().name(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            awaitBlocked(waiterPid, holderPid.get(), "orders");
            assertThat(waiter).isNotDone();
            assertThat(callbackCommitted).isFalse();
            assertThat(marker()).isNull(); // Joined callback write remains invisible until commit.
            release.countDown();
            assertThat(holder.get(15, TimeUnit.SECONDS)).isEqualTo("OK");
            assertThat(waiter.get(15, TimeUnit.SECONDS)).isEqualTo(returnFirst ? "CONFLICT" : "OK");
        } finally {
            release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(callbackCalled.get()).isEqualTo(!returnFirst);
        assertThat(callbackCommitted.get()).isEqualTo(!returnFirst);
        assertThat(marker()).isEqualTo(returnFirst ? null : "joined callback");
        assertReturnCommitted();
        assertThat(query.isCurrentBuyerEligibleToReview(item)).isFalse();
    }

    @Test @Timeout(45)
    void blockedAccountProvesOrderIsAcquiredBeforeAccountAndFreshStatusIsChecked() throws Exception {
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var holderPid = new AtomicInteger(); var waiterPid = new AtomicInteger();
        observeTransactionPid(waiterPid, "account-waiter");
        var pool = Executors.newFixedThreadPool(2);
        try {
            var holder = pool.submit(() -> new TransactionTemplate(manager).execute(s -> {
                holderPid.set(pid());
                jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", buyer);
                held.countDown(); await(release); return true;
            }));
            await(held);
            var waiter = pool.submit(() -> {
                Thread.currentThread().setName("account-waiter"); auth(buyer);
                try {
                    boundary.withEligibleCurrentBuyerItem(item, facts -> { throw new AssertionError("Locked account callback"); });
                    return "unexpected";
                } catch (ApplicationException error) { return error.errorCode().name(); }
                finally { SecurityContextHolder.clearContext(); }
            });
            awaitBlocked(waiterPid, holderPid.get(), "users");
            // A third transaction NOWAIT fails on this Order while boundary waits on Account.
            assertThatThrownBy(() -> new TransactionTemplate(manager).execute(s ->
                    jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE NOWAIT", Long.class, order)))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,
                            error -> assertThat(error.getSQLState()).isEqualTo("55P03"));
            release.countDown();
            assertThat(holder.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(waiter.get(15, TimeUnit.SECONDS)).isEqualTo("ACCESS_DENIED");
        } finally {
            release.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long user(String role) {
        String name = "rev" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("""
                INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                VALUES (?,?,?,?,'test-only-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, name + "@test.invalid", name + "@test.invalid", name, name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role);
        return id;
    }
    private long deliveredOrder(long owner, long shop) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,
                receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,
                commission_amount,commission_rate_snapshot,delivered_at)
                VALUES (?,?,'fixture-hash',?,?,'DELIVERED','Buyer','0900000000','VN','District','Address',100,0,0,100,0,0,?)
                RETURNING id
                """, Long.class, "REV-" + UUID.randomUUID(), UUID.randomUUID().toString(), owner, shop,
                Timestamp.from(Instant.now().minusSeconds(60)));
    }
    private long item(long parent) {
        return jdbc.queryForObject("""
                INSERT INTO uteexpress.order_items(order_id,product_id,product_name_snapshot,unit_price,
                discount_snapshot,final_unit_price,quantity,line_total) VALUES (?,?,'Snapshot',100,0,100,1,100) RETURNING id
                """, Long.class, parent, product);
    }
    private RequestReturnCommand request() { return new RequestReturnCommand(order, 0L, "Wrong item"); }
    private void assertReturnCommitted() {
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order)).isEqualTo("RETURN_REQUESTED");
        assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.orders WHERE id=?", Long.class, order)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.return_requests WHERE order_id=? AND status='PENDING'", Long.class, order)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=? AND to_status='RETURN_REQUESTED'", Long.class, order)).isEqualTo(1L);
    }
    private List<Object> state() {
        return List.of(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order),
                jdbc.queryForMap("SELECT * FROM uteexpress.order_items WHERE id=?", item),
                jdbc.queryForList("SELECT * FROM uteexpress.return_requests WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=?", order));
    }
    private String marker() { return jdbc.queryForObject("SELECT full_name FROM uteexpress.users WHERE id=?", String.class, buyer); }
    private void observeTransactionPid(AtomicInteger observed, String worker) {
        // Call the real transaction manager, then observe its connection before any lock SQL.
        // Repository queries and all PostgreSQL locking remain unmodified.
        doAnswer(call -> {
            Object result = call.callRealMethod();
            if (Thread.currentThread().getName().equals(worker) && observed.get() == 0)
                observed.set(pid());
            return result;
        }).when(manager).getTransaction(any(TransactionDefinition.class));
    }
    private int pid() {
        jdbc.execute("SET LOCAL lock_timeout='12s'");
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }
    private void awaitBlocked(AtomicInteger waiter, int holder, String table) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            int pid = waiter.get();
            if (pid > 0) {
                var row = jdbc.queryForMap("SELECT wait_event_type,query FROM pg_stat_activity WHERE pid=?", pid);
                if ("Lock".equals(row.get("wait_event_type")) && jdbc.queryForList(
                        "SELECT unnest(pg_blocking_pids(?))", Integer.class, pid).contains(holder)) {
                    assertThat(row.get("query").toString().toLowerCase(Locale.ROOT)).contains(table).contains("for no key update");
                    System.out.println("REV-01 PostgreSQL waiter=" + pid + " holder=" + holder + " sql=" + row.get("query"));
                    return;
                }
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected real PostgreSQL " + table + " waiter blocked by PID " + holder);
    }
    private static void auth(long id) { auth(id, "USER"); }
    private static void auth(long id, String role) {
        // Display username must never become an account ID source.
        var p = new UteExpressPrincipal(id, "untrusted-display-subject", null, 0L,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p, null, p.getAuthorities()));
    }
    private static void fails(ErrorCode code, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private static void beforeCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) { action.run(); }
        });
    }
    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
    @TestConfiguration static class Configuration {
        @Bean JoinedWriter eligibilityJoinedWriter(JdbcTemplate jdbc) { return new JoinedWriter(jdbc); }
    }
    /** Test-only joined persistence probe. No Review table/schema is invented. */
    static class JoinedWriter {
        private final JdbcTemplate jdbc;
        JoinedWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        @Transactional(propagation = Propagation.MANDATORY)
        public int write(Long buyer) {
            jdbc.update("UPDATE uteexpress.users SET full_name='joined callback' WHERE id=?", buyer);
            return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
        }
    }
}
