package com.uteexpress.order.service;

import com.uteexpress.catalog.service.InventoryService;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.service.*;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.payment.service.PaymentService;
import com.uteexpress.promotion.service.VoucherService;
import com.uteexpress.security.CurrentUserProvider;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shipping.dto.*;
import com.uteexpress.shipping.service.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL TD component tests. FixtureLifecycle is NOT the missing QD integration. */
@SpringBootTest @Testcontainers @RecordApplicationEvents
class OpsOrderCancellationIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderLifecycleService production;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired EntityManager entityManager;
    @Autowired CurrentUserProvider users;
    @Autowired CurrentAccountIdProvider accounts;
    @Autowired AccountIdentityService identities;
    @Autowired RoleGovernanceService roles;
    @Autowired InventoryService inventory;
    @Autowired VoucherService vouchers;
    @Autowired AuditLogService audit;
    @Autowired PaymentService payments;
    @Autowired ShipmentAssignmentService assignments;
    @Autowired ShipmentFulfillmentService shipments;
    @Autowired PlatformTransactionManager manager;
    @Autowired ApplicationEventPublisher publisher;
    @Autowired ApplicationEvents events;
    FixtureLifecycle fixture;
    OpsOrderAuthority authority;
    TransactionTemplate tx;
    CheckoutQuote quote;
    long order, vendor, buyer, admin, shipper, otherShipper, product;
    volatile boolean failAfterAudit;
    volatile Runnable beforeAccount = () -> { };
    volatile Runnable afterAudit = () -> { };

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        authority = new OpsOrderAuthority(orders, items, entityManager, accounts, identities, roles, inventory, vouchers, audit);
        fixture = new FixtureLifecycle();
        vendor = user("VENDOR"); buyer = user("USER"); admin = user("ADMIN");
        shipper = user("SHIPPER"); otherShipper = user("SHIPPER");
        long shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, vendor, UUID.randomUUID().toString());
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id", Long.class, UUID.randomUUID().toString());
        product = jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',100000,8,'ACTIVE') RETURNING id", Long.class, shop, category);
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id", Long.class, "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        quote = new CheckoutQuote(shop, List.of(new CheckoutQuote.ItemSnapshot(product, "Historical product", new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"), 1, new BigDecimal("100000"))),
                new CheckoutQuote.AddressSnapshot("Receiver", "0900000000", "VN", "District", "Address"),
                OrderTotals.calculate(new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO),
                provider, "STANDARD", null, BigDecimal.ZERO, BigDecimal.ZERO);
        createOrder(quote);
        auth(admin, "ADMIN"); events.clear();
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER"})
    void persistedOpsRolesAuthorizeTdComponents(String role) {
        long actor = user(role); auth(actor, role);
        var event = fixture.cancelForOps(command());
        assertThat(event.actorId()).isEqualTo(actor);
        assertThat(status()).isEqualTo("CANCELLED");
        assertThat(event.reason()).isEqualTo(OpsCancellationReason.UNABLE_TO_FULFILL.description());
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "VENDOR", "SHIPPER"})
    void otherRolesCannotEnterProductionOrTdBoundary(String role) {
        auth(user(role), role); var before = state();
        assertThatThrownBy(() -> production.cancelForOps(command())).isInstanceOf(AccessDeniedException.class);
        rejects(() -> fixture.cancelForOps(command()), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
    }

    @Test void unauthenticatedActorIsDenied() {
        SecurityContextHolder.clearContext();
        rejects(() -> fixture.cancelForOps(command()), ErrorCode.UNAUTHENTICATED);
    }

    @Test void inactivePersistedAccountIsDenied() {
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", admin);
        var before = state(); rejects(() -> fixture.cancelForOps(command()), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER"})
    void staleAuthenticatedRoleDoesNotOverridePersistedRevocation(String role) {
        long actor = user(role); auth(actor, role);
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?", actor);
        var before = state(); rejects(() -> fixture.cancelForOps(command()), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
    }

    @Test void productionFailsClosedWithoutQdHookEvenForNoShipment() {
        var before = state(); rejects(() -> production.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void joinedTransactionsAreRejectedEvenWhenCallerAlreadyHoldsAccount(boolean holdAccount) {
        var before = state();
        tx.executeWithoutResult(s -> {
            if (holdAccount) identities.requireActiveAccountForUpdate(admin);
            rejects(() -> production.cancelForOps(command()), ErrorCode.INVALID_REQUEST);
        });
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @CsvSource({"EXPECTED_ABSENT,0", "EXPECTED_PRESENT,", "EXPECTED_PRESENT,-1"})
    void invalidShipmentExpectationContractsAreRejected(OpsShipmentExpectation expectation, Long version) {
        var command = new OpsOrderCancellationCommand(order, OrderStatus.NEW, 0L, expectation, version, OpsCancellationReason.UNABLE_TO_FULFILL);
        rejects(() -> production.cancelForOps(command), ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void newAndConfirmedWithoutShipmentCancelExactlyOnceInTdFixture(boolean confirmed) {
        if (confirmed) confirm(false);
        var command = command(); var payment = paymentState(); var original = orders.findById(order).orElseThrow();
        fixture.cancelForOps(command);
        assertThat(stock()).isEqualTo(9);
        assertThat(paymentState()).isEqualTo(payment);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=? AND to_status='CANCELLED'", Integer.class, order)).isEqualTo(1);
        var auditRow = jdbc.queryForMap("SELECT * FROM uteexpress.audit_logs WHERE target_type='ORDER' AND target_id=?", order);
        assertThat(auditRow).containsEntry("actor_id", admin).containsEntry("action", "ORDER_OPS_CANCELLED").containsEntry("reason", "UNABLE_TO_FULFILL");
        var cancelled = orders.findById(order).orElseThrow();
        assertThat(cancelled.getReceiverName()).isEqualTo(original.getReceiverName());
        assertThat(cancelled.getShippingProviderId()).isEqualTo(original.getShippingProviderId());
        assertThat(cancelled.getGrandTotal()).isEqualTo(original.getGrandTotal());
        assertThat(cancelled.getInventoryReleasedAt()).isEqualTo(cancelled.getCancelledAt());
        assertThat(events.stream(OrderStatusChangedEvent.class).count()).isEqualTo(1);
        var after = state(); rejects(() -> fixture.cancelForOps(command), ErrorCode.CONFLICT);
        rejects(() -> fixture.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(after);
    }

    @Test void cleanAssignedFixtureCancellationUsesOriginalShipmentVersion() {
        confirm(true); assign(); var payment = paymentState();
        fixture.cancelForOps(command());
        assertThat(status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?", String.class, order)).isEqualTo("CANCELLED");
        assertThat(shipmentVersion()).isEqualTo(1);
        assertThat(stock()).isEqualTo(9); assertThat(paymentState()).isEqualTo(payment);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void staleOrderVersionOrStatusRejectsBeforeEffects(boolean staleStatus) {
        confirm(false);
        var stale = new OpsOrderCancellationCommand(order, staleStatus ? OrderStatus.NEW : OrderStatus.CONFIRMED,
                staleStatus ? 1L : 0L, OpsShipmentExpectation.EXPECTED_ABSENT, null, OpsCancellationReason.UNABLE_TO_FULFILL);
        var before = state(); rejects(() -> fixture.cancelForOps(stale), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void newlyAssignedShipmentAndStaleShipmentVersionAreConflicts(boolean staleVersion) {
        confirm(true); var absent = command(); assign();
        var stale = staleVersion ? new OpsOrderCancellationCommand(order, OrderStatus.CONFIRMED, orderVersion(),
                OpsShipmentExpectation.EXPECTED_PRESENT, 99L, OpsCancellationReason.UNABLE_TO_FULFILL) : absent;
        var before = state(); rejects(() -> fixture.cancelForOps(stale), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void expectedPresentCannotAcceptMissingShipment() {
        var present = new OpsOrderCancellationCommand(order, OrderStatus.NEW, 0L, OpsShipmentExpectation.EXPECTED_PRESENT, 0L, OpsCancellationReason.UNABLE_TO_FULFILL);
        rejects(() -> fixture.cancelForOps(present), ErrorCode.CONFLICT);
    }

    @ParameterizedTest @ValueSource(strings = {"picked_up_at=CURRENT_TIMESTAMP", "delivered_at=CURRENT_TIMESTAMP", "attempt_count=1", "status='PICKED_UP'"})
    void physicalOrInconsistentShipmentEvidenceIsRejected(String corruption) {
        confirm(true); assign(); jdbc.update("UPDATE uteexpress.shipments SET " + corruption + " WHERE order_id=?", order);
        var before = state(); rejects(() -> fixture.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = {"NEW", "CONFIRMED"}, mode = EnumSource.Mode.EXCLUDE)
    void laterAndTerminalOrderStatesAreRejected(OrderStatus status) {
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?", status.name(), order);
        var before = state(); rejects(() -> fixture.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void voucherUsageReleasesOnceAndHistoricalSnapshotSurvives() {
        withVoucher(); var original = jdbc.queryForMap("SELECT voucher_id,voucher_code,voucher_scope,discount_total FROM uteexpress.orders WHERE id=?", order);
        fixture.cancelForOps(command());
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.voucher_usages WHERE order_id=?", String.class, order)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForMap("SELECT voucher_id,voucher_code,voucher_scope,discount_total FROM uteexpress.orders WHERE id=?", order)).isEqualTo(original);
        var after = state(); rejects(() -> fixture.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(after);
    }

    @ParameterizedTest @ValueSource(strings = {"AUDIT", "STOCK", "VOUCHER"})
    void failureRollsBackAllTdEffectsAndFixtureShipmentAndNoEventEscapes(String failure) {
        withVoucher(); confirm(true); assign();
        if (failure.equals("AUDIT")) failAfterAudit = true;
        if (failure.equals("STOCK")) jdbc.update("UPDATE uteexpress.products SET stock=2147483647 WHERE id=?", product);
        if (failure.equals("VOUCHER")) jdbc.update("UPDATE uteexpress.voucher_usages SET discount_amount=1 WHERE order_id=?", order);
        var before = state();
        assertThatThrownBy(() -> fixture.cancelForOps(command())).isInstanceOf(RuntimeException.class);
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    enum Race { VENDOR_CANCEL, PICKUP, ASSIGN, REASSIGN, CONFIRM }

    @ParameterizedTest @CsvSource({"VENDOR_CANCEL,true", "VENDOR_CANCEL,false", "PICKUP,true", "PICKUP,false",
            "ASSIGN,true", "ASSIGN,false", "REASSIGN,true", "REASSIGN,false", "CONFIRM,true", "CONFIRM,false"})
    @Timeout(60)
    void tdFixtureCancellationSerializesWithRealContendersInBothDirections(Race race, boolean opsFirst) throws Exception {
        if (race == Race.PICKUP || race == Race.ASSIGN || race == Race.REASSIGN) confirm(true);
        if (race == Race.PICKUP || race == Race.REASSIGN) assign();
        var original = command(); var payment = paymentState();
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var holderPid = new CompletableFuture<Integer>(); var waiterPid = new CompletableFuture<Integer>();
        var attempts = new AtomicInteger();
        beforeAccount = () -> {
            if (opsFirst) { holderPid.complete(pid()); held.countDown(); await(release); }
            else waiterPid.complete(pid());
        };
        // For Ops-second, capture PID before it waits for Order (the Shipment hook is too late).
        fixture.onEntry = () -> { if (!opsFirst) waiterPid.complete(pid()); };
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> opsFirst ? ops(original) : contender(race, original, true, holderPid, held, release, attempts));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> opsFirst ? contender(race, original, false, waiterPid, held, release, attempts) : ops(original));
            int holder = holderPid.get(10, TimeUnit.SECONDS), waiter = waiterPid.get(10, TimeUnit.SECONDS);
            observeOrderWait(holder, waiter);
            release.countDown();
            assertThat(first.get(25, TimeUnit.SECONDS)).isEqualTo("OK");
            assertThat(second.get(25, TimeUnit.SECONDS)).isEqualTo("CONFLICT");
        } finally { release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        assertThat(paymentState()).isEqualTo(payment);
        assertThat(stock()).isEqualTo(opsFirst || race == Race.VENDOR_CANCEL ? 9 : 8);
        assertThat(status()).isEqualTo(opsFirst || race == Race.VENDOR_CANCEL ? "CANCELLED" : race == Race.PICKUP ? "PICKED_UP" : "CONFIRMED");
        assertThat(orderVersion()).isEqualTo(original.expectedOrderVersion() + (opsFirst || race == Race.VENDOR_CANCEL || race == Race.PICKUP || race == Race.CONFIRM ? 1 : 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='ORDER' AND target_id=?", Integer.class, order)).isEqualTo(opsFirst ? 1 : 0);
    }

    private String ops(OpsOrderCancellationCommand command) {
        auth(admin, "ADMIN");
        try { fixture.cancelForOps(command); return "OK"; }
        catch (ApplicationException e) { return e.errorCode().name(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private String contender(Race race, OpsOrderCancellationCommand original, boolean hold,
            CompletableFuture<Integer> backend, CountDownLatch held, CountDownLatch release, AtomicInteger attempts) {
        auth(race == Race.PICKUP ? shipper : race == Race.ASSIGN || race == Race.REASSIGN ? admin : vendor,
                race == Race.PICKUP ? "SHIPPER" : race == Race.ASSIGN || race == Race.REASSIGN ? "ADMIN" : "VENDOR");
        try {
            return tx.execute(s -> {
                jdbc.execute("SET LOCAL lock_timeout='20s'"); backend.complete(pid());
                orders.findByIdForUpdate(order).orElseThrow();
                switch (race) {
                    case VENDOR_CANCEL -> production.transition(new OrderTransitionCommand(order, original.expectedOrderStatus(), original.expectedOrderVersion(), OrderAction.CANCEL_NEW, "UNABLE_TO_FULFILL"));
                    case CONFIRM -> production.transition(new OrderTransitionCommand(order, OrderStatus.NEW, original.expectedOrderVersion(), OrderAction.CONFIRM, null));
                    case ASSIGN -> assignments.assign(order, shipper, original.expectedOrderVersion());
                    case REASSIGN -> assignments.reassign(order, otherShipper, original.expectedShipmentVersion());
                    case PICKUP -> {
                        production.prepareShipperTransition(order, original.expectedOrderVersion(), original.expectedShipmentVersion(), OrderAction.PICK_UP);
                        shipments.recordPickedUp(order, original.expectedShipmentVersion());
                        production.completeShipperTransition(order, original.expectedOrderVersion(), original.expectedShipmentVersion(), OrderAction.PICK_UP);
                    }
                }
                attempts.incrementAndGet();
                if (hold) { held.countDown(); await(release); }
                return "OK";
            });
        } catch (ApplicationException e) { return e.errorCode().name(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void observeOrderWait(int holder, int waiter) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            var row = jdbc.queryForMap("SELECT wait_event_type,query FROM pg_stat_activity WHERE pid=?", waiter);
            var blockers = jdbc.queryForList("SELECT unnest(pg_blocking_pids(?))", Integer.class, waiter);
            if ("Lock".equals(row.get("wait_event_type")) && blockers.contains(holder)) {
                assertThat(row.get("query").toString()).containsIgnoringCase("orders");
                System.out.println("ORD-OPS-01 TD fixture waiter=" + waiter + " holder=" + holder + " sql=" + row.get("query"));
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected actual PostgreSQL Order blocker");
    }

    /** Explicit test double only. No production bean implements either QD hook. */
    final class FixtureLifecycle extends OrderLifecycleServiceImpl {
        Runnable onEntry = () -> { };
        FixtureLifecycle() {
            super(OpsOrderCancellationIT.this.orders, OpsOrderCancellationIT.this.items,
                    OpsOrderCancellationIT.this.history, OpsOrderCancellationIT.this.users,
                    OpsOrderCancellationIT.this.manager, OpsOrderCancellationIT.this.publisher, Clock.systemUTC());
        }
        @Override protected Order loadForOpsMutation(Long id) {
            jdbc.execute("SET LOCAL lock_timeout='20s'"); onEntry.run(); return authority.lockOrder(id);
        }
        @Override protected Optional<ShipmentFulfillmentFacts> lockShipmentForOps(Order order, OpsOrderCancellationCommand command) {
            var rows = jdbc.query("SELECT id,order_id,assigned_shipper_id,status,version,attempt_count,max_attempts,picked_up_at,delivered_at FROM uteexpress.shipments WHERE order_id=? FOR UPDATE",
                    (rs, ignored) -> new ShipmentFulfillmentFacts(rs.getLong(1), rs.getLong(2), rs.getLong(3), ShipmentStatus.valueOf(rs.getString(4)), rs.getLong(5), rs.getInt(6), rs.getInt(7),
                            rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant()), order.getId());
            beforeAccount.run(); return rows.stream().findFirst();
        }
        @Override protected Long authorizeOps(Order order) { return authority.authorize(); }
        @Override protected void resolveShipmentForOps(Order order, Optional<ShipmentFulfillmentFacts> shipment, Instant at) {
            shipment.ifPresent(facts -> assertThat(jdbc.update("UPDATE uteexpress.shipments SET status='CANCELLED',version=version+1,updated_at=? WHERE id=? AND version=?", java.sql.Timestamp.from(at), facts.shipmentId(), facts.version())).isEqualTo(1));
        }
        @Override protected void beforeOpsCancellation(Order order, Instant at) { authority.release(order, at); }
        @Override protected void auditOpsCancellation(Order order, OrderStatus from, Long version, Long actor, OpsCancellationReason reason) {
            authority.appendAudit(order, from, version, actor, reason);
            assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
            if (failAfterAudit) throw new IllegalStateException("Injected failure after Order/history/audit flush");
            afterAudit.run();
        }
    }

    private void createOrder(CheckoutQuote snapshot) {
        tx.executeWithoutResult(s -> {
            var entity = orders.saveAndFlush(new Order(buyer, "OPS-" + UUID.randomUUID(), UUID.randomUUID().toString(), "hash", snapshot, Instant.now()));
            order = entity.getId(); items.saveAllAndFlush(snapshot.items().stream().map(i -> new OrderItem(order, i)).toList());
            history.saveAndFlush(new OrderStatusHistory(order, null, OrderStatus.NEW, buyer, entity.getCreatedAt(), null));
            if (snapshot.voucher() != null) vouchers.recordUsage(snapshot.voucher(), order);
            payments.initializeCodForNewOrder(order);
        });
    }

    private void withVoucher() {
        String code = "SAVE" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbc.update("INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by) VALUES (?,'SHOP','FIXED',?,10000,0,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '1 day',10,10,?)", code, quote.shopId(), vendor);
        auth(buyer, "USER"); tx.executeWithoutResult(s -> {
            var voucher = vouchers.lockForCheckout(code, quote.shopId(), quote.totals().subtotal());
            createOrder(new CheckoutQuote(quote.shopId(), quote.items(), quote.address(), OrderTotals.calculate(quote.totals().subtotal(), voucher.discountAmount(), BigDecimal.ZERO), quote.shippingProviderId(), "STANDARD", null, BigDecimal.ZERO, BigDecimal.ZERO, voucher));
        }); auth(admin, "ADMIN"); events.clear();
    }

    private void confirm(boolean ready) {
        auth(vendor, "VENDOR"); production.transition(new OrderTransitionCommand(order, OrderStatus.NEW, 0L, OrderAction.CONFIRM, null));
        if (ready) production.markReady(new OrderReadyCommand(order, 1L));
        auth(admin, "ADMIN"); events.clear();
    }
    private void assign() { assignments.assign(order, shipper, orderVersion()); events.clear(); }
    private OpsOrderCancellationCommand command() {
        Long sv = jdbc.query("SELECT version FROM uteexpress.shipments WHERE order_id=?", (rs, i) -> rs.getLong(1), order).stream().findFirst().orElse(null);
        return new OpsOrderCancellationCommand(order, OrderStatus.valueOf(status()), orderVersion(), sv == null ? OpsShipmentExpectation.EXPECTED_ABSENT : OpsShipmentExpectation.EXPECTED_PRESENT, sv, OpsCancellationReason.UNABLE_TO_FULFILL);
    }
    private int pid() { return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class); }
    private String status() { return jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order); }
    private long orderVersion() { return jdbc.queryForObject("SELECT version FROM uteexpress.orders WHERE id=?", Long.class, order); }
    private long shipmentVersion() { return jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?", Long.class, order); }
    private int stock() { return jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, product); }
    private List<Map<String, Object>> paymentState() { return jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=? ORDER BY id", order); }
    private List<List<Map<String, Object>>> state() {
        return List.of(jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.order_items WHERE order_id=? ORDER BY id", order), paymentState(),
                jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", order),
                jdbc.queryForList("SELECT * FROM uteexpress.products WHERE id=?", product),
                jdbc.queryForList("SELECT * FROM uteexpress.shipments WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='ORDER' AND target_id=? ORDER BY id", order));
    }
    private long user(String role) {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test-only-password','ACTIVE') RETURNING id", Long.class, name + "@example.test", name + "@example.test", name, name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role); return id;
    }
    private static void auth(long id, String role) {
        var principal = new UteExpressPrincipal(id, "opaque", null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    private static void rejects(Runnable operation, ErrorCode code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
