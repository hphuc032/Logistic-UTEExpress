package com.uteexpress.order.service;

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
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.shipping.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** PostgreSQL E2E tests of the production Order bean and mandatory Shipping hooks. */
@SpringBootTest @Testcontainers @RecordApplicationEvents
class OpsOrderCancellationIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderLifecycleService production;
    @MockitoSpyBean OrderPlacementService lifecycle;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired AccountIdentityService identities;
    @Autowired VoucherService vouchers;
    @Autowired PaymentService payments;
    @Autowired ShipmentAssignmentService assignments;
    @MockitoSpyBean ShipmentFulfillmentService shipments;
    @Autowired PlatformTransactionManager manager;
    @Autowired ApplicationEvents events;
    TransactionTemplate tx;
    CheckoutQuote quote;
    long order, vendor, buyer, admin, shipper, otherShipper, product;
    volatile boolean failAfterAudit;
    volatile Runnable beforeAccount = () -> { };
    volatile Runnable onEntry = () -> { };

    @BeforeEach void setup() {
        tx = new TransactionTemplate(manager);
        OrderPlacementService target = AopTestUtils.getUltimateTargetObject(lifecycle);
        doAnswer(call -> {
            jdbc.execute("SET LOCAL lock_timeout='20s'"); onEntry.run();
            return call.callRealMethod();
        }).when(target).loadForOpsMutation(anyLong());
        doAnswer(call -> { beforeAccount.run(); return call.callRealMethod(); })
                .when(target).authorizeOps(any(Order.class));
        doAnswer(call -> {
            var result = call.callRealMethod();
            assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
            if (failAfterAudit) throw new IllegalStateException("Injected failure after Order/history/audit flush");
            return result;
        }).when(target).auditOpsCancellation(any(Order.class), any(OrderStatus.class), anyLong(), anyLong(), any(OpsCancellationReason.class));
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
    void persistedOpsRolesAuthorizeProductionBoundary(String role) {
        long actor = user(role); auth(actor, role);
        var event = production.cancelForOps(command());
        assertThat(event.actorId()).isEqualTo(actor);
        assertThat(status()).isEqualTo("CANCELLED");
        assertThat(event.reason()).isEqualTo(OpsCancellationReason.UNABLE_TO_FULFILL.description());
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "VENDOR", "SHIPPER"})
    void otherRolesCannotEnterProductionBoundary(String role) {
        auth(user(role), role); var before = state();
        assertThatThrownBy(() -> production.cancelForOps(command())).isInstanceOf(AccessDeniedException.class);
        assertThat(state()).isEqualTo(before);
    }

    @Test void unauthenticatedActorIsDenied() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> production.cancelForOps(command())).isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test void inactivePersistedAccountIsDenied() {
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", admin);
        var before = state(); rejects(() -> production.cancelForOps(command()), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER"})
    void staleAuthenticatedRoleDoesNotOverridePersistedRevocation(String role) {
        long actor = user(role); auth(actor, role);
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?", actor);
        var before = state(); rejects(() -> production.cancelForOps(command()), ErrorCode.ACCESS_DENIED);
        assertThat(state()).isEqualTo(before);
    }

    @Test void productionCallsBothShippingHooksForAuthoritativeAbsence() {
        production.cancelForOps(command());
        verify(shipments).lockForOpsCancellation(order);
        verify(shipments).cancelLockedForOpsOrder(order, null);
        assertThat(status()).isEqualTo("CANCELLED");
        assertThat(shipmentAudits()).isEmpty();
        assertThat(events.stream(OrderStatusChangedEvent.class).count()).isEqualTo(1);
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
    void newAndConfirmedWithoutShipmentCancelExactlyOnce(boolean confirmed) {
        if (confirmed) confirm(false);
        var command = command(); var payment = paymentState(); var original = orders.findById(order).orElseThrow();
        production.cancelForOps(command);
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
        var after = state(); rejects(() -> production.cancelForOps(command), ErrorCode.CONFLICT);
        rejects(() -> production.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(after);
    }

    @Test void cleanAssignedProductionCancellationUsesOriginalShipmentVersion() {
        confirm(true); assign(); var payment = paymentState();
        var original = jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE order_id=?", order);
        production.cancelForOps(command());
        assertThat(status()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?", String.class, order)).isEqualTo("CANCELLED");
        assertThat(shipmentVersion()).isEqualTo(1);
        assertThat(stock()).isEqualTo(9); assertThat(paymentState()).isEqualTo(payment);
        verify(shipments).lockForOpsCancellation(order);
        verify(shipments).cancelLockedForOpsOrder(order, 0L);
        var cancelled = jdbc.queryForMap("SELECT * FROM uteexpress.shipments WHERE order_id=?", order);
        for (String field : List.of("provider_id", "assigned_shipper_id", "shipping_service_snapshot", "fee_snapshot",
                "attempt_count", "max_attempts", "picked_up_at", "delivered_at", "created_at")) {
            assertThat(cancelled.get(field)).as(field).isEqualTo(original.get(field));
        }
        assertThat(shipmentAudits()).filteredOn(row -> row.get("action").equals("SHIPMENT_CANCELLED"))
                .singleElement().satisfies(row -> assertThat(row).containsEntry("actor_id", admin)
                        .containsEntry("reason", "UNABLE_TO_FULFILL").containsEntry("before_summary", "status=ASSIGNED;version=0")
                        .containsEntry("after_summary", "status=CANCELLED;version=1"));
        var after = state(); rejects(() -> production.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(after);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void staleOrderVersionOrStatusRejectsBeforeEffects(boolean staleStatus) {
        confirm(false);
        var stale = new OpsOrderCancellationCommand(order, staleStatus ? OrderStatus.NEW : OrderStatus.CONFIRMED,
                staleStatus ? 1L : 0L, OpsShipmentExpectation.EXPECTED_ABSENT, null, OpsCancellationReason.UNABLE_TO_FULFILL);
        var before = state(); rejects(() -> production.cancelForOps(stale), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void newlyAssignedShipmentAndStaleShipmentVersionAreConflicts(boolean staleVersion) {
        confirm(true); var absent = command(); assign();
        var stale = staleVersion ? new OpsOrderCancellationCommand(order, OrderStatus.CONFIRMED, orderVersion(),
                OpsShipmentExpectation.EXPECTED_PRESENT, 99L, OpsCancellationReason.UNABLE_TO_FULFILL) : absent;
        var before = state(); rejects(() -> production.cancelForOps(stale), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void expectedPresentCannotAcceptMissingShipment() {
        var present = new OpsOrderCancellationCommand(order, OrderStatus.NEW, 0L, OpsShipmentExpectation.EXPECTED_PRESENT, 0L, OpsCancellationReason.UNABLE_TO_FULFILL);
        rejects(() -> production.cancelForOps(present), ErrorCode.CONFLICT);
    }

    @ParameterizedTest @ValueSource(strings = {"picked_up_at=CURRENT_TIMESTAMP", "delivered_at=CURRENT_TIMESTAMP", "attempt_count=1", "status='PICKED_UP'"})
    void physicalOrInconsistentShipmentEvidenceIsRejected(String corruption) {
        confirm(true); assign(); jdbc.update("UPDATE uteexpress.shipments SET " + corruption + " WHERE order_id=?", order);
        var before = state(); rejects(() -> production.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"NEW_READY", "NEW_ASSIGNED", "CONFIRMED_NOT_READY", "RELEASED", "CANCELLED_AT", "CANCELLATION_REASON", "DELIVERED_AT"})
    void inconsistentOrderEvidenceIsRejectedBeforeShippingMutation(String kind) {
        if (kind.equals("NEW_ASSIGNED") || kind.equals("CONFIRMED_NOT_READY")) { confirm(true); assign(); }
        switch (kind) {
            case "NEW_READY" -> jdbc.update("UPDATE uteexpress.orders SET ready_at=CURRENT_TIMESTAMP WHERE id=?", order);
            case "NEW_ASSIGNED" -> jdbc.update("UPDATE uteexpress.orders SET status='NEW' WHERE id=?", order);
            case "CONFIRMED_NOT_READY" -> jdbc.update("UPDATE uteexpress.orders SET ready_at=NULL WHERE id=?", order);
            case "RELEASED" -> jdbc.update("UPDATE uteexpress.orders SET inventory_released_at=CURRENT_TIMESTAMP WHERE id=?", order);
            case "CANCELLED_AT" -> jdbc.update("UPDATE uteexpress.orders SET cancelled_at=CURRENT_TIMESTAMP WHERE id=?", order);
            case "CANCELLATION_REASON" -> jdbc.update("UPDATE uteexpress.orders SET cancellation_reason='UNABLE_TO_FULFILL' WHERE id=?", order);
            case "DELIVERED_AT" -> jdbc.update("UPDATE uteexpress.orders SET delivered_at=CURRENT_TIMESTAMP WHERE id=?", order);
        }
        var before = state(); rejects(() -> production.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        verify(shipments, never()).cancelLockedForOpsOrder(anyLong(), nullable(Long.class));
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = {"NEW", "CONFIRMED"}, mode = EnumSource.Mode.EXCLUDE)
    void laterAndTerminalOrderStatesAreRejected(OrderStatus status) {
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?", status.name(), order);
        var before = state(); rejects(() -> production.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void voucherUsageReleasesOnceAndHistoricalSnapshotSurvives() {
        withVoucher(); var original = jdbc.queryForMap("SELECT voucher_id,voucher_code,voucher_scope,discount_total FROM uteexpress.orders WHERE id=?", order);
        production.cancelForOps(command());
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.voucher_usages WHERE order_id=?", String.class, order)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForMap("SELECT voucher_id,voucher_code,voucher_scope,discount_total FROM uteexpress.orders WHERE id=?", order)).isEqualTo(original);
        var after = state(); rejects(() -> production.cancelForOps(command()), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(after);
    }

    @ParameterizedTest @ValueSource(strings = {"AUDIT", "STOCK", "VOUCHER"})
    void failureRollsBackAllProductionEffectsAndNoEventEscapes(String failure) {
        withVoucher(); confirm(true); assign();
        if (failure.equals("AUDIT")) failAfterAudit = true;
        if (failure.equals("STOCK")) jdbc.update("UPDATE uteexpress.products SET stock=2147483647 WHERE id=?", product);
        if (failure.equals("VOUCHER")) jdbc.update("UPDATE uteexpress.voucher_usages SET discount_amount=1 WHERE order_id=?", order);
        var before = state();
        assertThatThrownBy(() -> production.cancelForOps(command())).isInstanceOf(RuntimeException.class);
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"READ", "CANCEL", "AUDIT"})
    void shippingHookFailureRollsBackEntireProductionCancellation(String failure) {
        withVoucher(); confirm(true); assign();
        ShipmentFulfillmentService target = AopTestUtils.getUltimateTargetObject(shipments);
        if (failure.equals("READ")) {
            doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Shipping read failed"); })
                    .when(target).lockForOpsCancellation(order);
        }
        if (failure.equals("CANCEL")) {
            doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Shipping cancel failed after mutation/audit"); })
                    .when(target).cancelLockedForOpsOrder(order, 0L);
        }
        if (failure.equals("AUDIT")) jdbc.execute("ALTER TABLE uteexpress.audit_logs ADD CONSTRAINT ops_integration_audit_failure CHECK (action <> 'SHIPMENT_CANCELLED') NOT VALID");
        var before = state();
        try {
            assertThatThrownBy(() -> production.cancelForOps(command())).isInstanceOf(
                    failure.equals("AUDIT") ? org.springframework.dao.DataIntegrityViolationException.class : IllegalStateException.class);
        } finally {
            if (failure.equals("AUDIT")) jdbc.execute("ALTER TABLE uteexpress.audit_logs DROP CONSTRAINT ops_integration_audit_failure");
        }
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    enum Race { VENDOR_CANCEL, VENDOR_CANCEL_ASSIGNED, PICKUP, ASSIGN, REASSIGN, CONFIRM }

    @ParameterizedTest @CsvSource({"VENDOR_CANCEL,true", "VENDOR_CANCEL,false", "PICKUP,true", "PICKUP,false",
            "ASSIGN,true", "ASSIGN,false", "REASSIGN,true", "REASSIGN,false", "CONFIRM,true", "CONFIRM,false",
            "VENDOR_CANCEL_ASSIGNED,true", "VENDOR_CANCEL_ASSIGNED,false"})
    @Timeout(60)
    void productionCancellationSerializesWithRealContendersInBothDirections(Race race, boolean opsFirst) throws Exception {
        withVoucher();
        if (race == Race.PICKUP || race == Race.ASSIGN || race == Race.REASSIGN || race == Race.VENDOR_CANCEL_ASSIGNED) confirm(true);
        if (race == Race.PICKUP || race == Race.REASSIGN || race == Race.VENDOR_CANCEL_ASSIGNED) assign();
        var original = command(); var payment = paymentState();
        var snapshots = snapshots();
        int originalHistory = jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=?", Integer.class, order);
        int originalAssignments = assignmentHistory().size();
        int originalShipmentAudits = shipmentAudits().size();
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var holderPid = new CompletableFuture<Integer>(); var waiterPid = new CompletableFuture<Integer>();
        beforeAccount = () -> {
            if (opsFirst) { holderPid.complete(pid()); held.countDown(); await(release); }
            else waiterPid.complete(pid());
        };
        // For Ops-second, capture PID before it waits for Order (the Shipment hook is too late).
        onEntry = () -> { if (!opsFirst) waiterPid.complete(pid()); };
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> opsFirst ? ops(original) : contender(race, original, true, holderPid, held, release));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> opsFirst ? contender(race, original, false, waiterPid, held, release) : ops(original));
            int holder = holderPid.get(10, TimeUnit.SECONDS), waiter = waiterPid.get(10, TimeUnit.SECONDS);
            assertThat(holder).isNotEqualTo(waiter);
            observeOrderWait(holder, waiter, race, opsFirst);
            release.countDown();
            assertThat(first.get(25, TimeUnit.SECONDS)).isEqualTo("OK");
            assertThat(second.get(25, TimeUnit.SECONDS)).isEqualTo("CONFLICT");
        } finally { release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        assertThat(paymentState()).isEqualTo(payment);
        assertThat(this.snapshots()).isEqualTo(snapshots);
        boolean cancelled = opsFirst || race == Race.VENDOR_CANCEL || race == Race.VENDOR_CANCEL_ASSIGNED;
        assertThat(stock()).isEqualTo(cancelled ? 9 : 8);
        assertThat(status()).isEqualTo(cancelled ? "CANCELLED" : race == Race.PICKUP ? "PICKED_UP" : "CONFIRMED");
        assertThat(orderVersion()).isEqualTo(original.expectedOrderVersion() + (cancelled || race == Race.PICKUP || race == Race.CONFIRM ? 1 : 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='ORDER' AND target_id=?", Integer.class, order)).isEqualTo(opsFirst ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.voucher_usages WHERE order_id=?", String.class, order))
                .isEqualTo(cancelled ? "RELEASED" : "REDEEMED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=?", Integer.class, order))
                .isEqualTo(originalHistory + (cancelled || race == Race.PICKUP || race == Race.CONFIRM ? 1 : 0));
        var shipment = jdbc.queryForList("SELECT * FROM uteexpress.shipments WHERE order_id=?", order);
        if (race == Race.PICKUP || race == Race.REASSIGN || race == Race.VENDOR_CANCEL_ASSIGNED || (!opsFirst && race == Race.ASSIGN)) {
            assertThat(shipment).singleElement().satisfies(row -> {
                assertThat(row.get("status")).isEqualTo(cancelled ? "CANCELLED" : race == Race.PICKUP ? "PICKED_UP" : "ASSIGNED");
                assertThat(row.get("version")).isEqualTo(race == Race.ASSIGN ? 0L : 1L);
                assertThat(row.get("assigned_shipper_id")).isEqualTo(!opsFirst && race == Race.REASSIGN ? otherShipper : shipper);
                assertThat(row.get("picked_up_at")).matches(value -> (!opsFirst && race == Race.PICKUP) == (value != null));
                assertThat(row.get("delivered_at")).isNull();
                assertThat(row.get("attempt_count")).isEqualTo(0);
            });
        } else assertThat(shipment).isEmpty();
        assertThat(assignmentHistory()).hasSize(originalAssignments + (!opsFirst && (race == Race.ASSIGN || race == Race.REASSIGN) ? 1 : 0));
        assertThat(shipmentAudits()).hasSize(originalShipmentAudits + (shipment.isEmpty() ? 0 : 1));
        if (opsFirst && !shipment.isEmpty()) {
            assertThat(shipmentAudits()).last().satisfies(row -> assertThat(row)
                    .containsEntry("actor_id", admin).containsEntry("action", "SHIPMENT_CANCELLED").containsEntry("reason", "UNABLE_TO_FULFILL"));
        }
    }

    private String ops(OpsOrderCancellationCommand command) {
        auth(admin, "ADMIN");
        try { production.cancelForOps(command); return "OK"; }
        catch (ApplicationException e) { return e.errorCode().name(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private String contender(Race race, OpsOrderCancellationCommand original, boolean hold,
            CompletableFuture<Integer> backend, CountDownLatch held, CountDownLatch release) {
        auth(race == Race.PICKUP ? shipper : race == Race.ASSIGN || race == Race.REASSIGN ? admin : vendor,
                race == Race.PICKUP ? "SHIPPER" : race == Race.ASSIGN || race == Race.REASSIGN ? "ADMIN" : "VENDOR");
        try {
            return tx.execute(s -> {
                jdbc.execute("SET LOCAL lock_timeout='20s'"); backend.complete(pid());
                switch (race) {
                    case VENDOR_CANCEL -> production.transition(new OrderTransitionCommand(order, original.expectedOrderStatus(), original.expectedOrderVersion(), OrderAction.CANCEL_NEW, "UNABLE_TO_FULFILL"));
                    case VENDOR_CANCEL_ASSIGNED -> production.transition(new OrderTransitionCommand(order, original.expectedOrderStatus(), original.expectedOrderVersion(), OrderAction.CANCEL_CONFIRMED, "UNABLE_TO_FULFILL"));
                    case CONFIRM -> production.transition(new OrderTransitionCommand(order, OrderStatus.NEW, original.expectedOrderVersion(), OrderAction.CONFIRM, null));
                    case ASSIGN -> assignments.assign(order, shipper, original.expectedOrderVersion());
                    case REASSIGN -> assignments.reassign(order, otherShipper, original.expectedShipmentVersion());
                    case PICKUP -> {
                        production.prepareShipperTransition(order, original.expectedOrderVersion(), original.expectedShipmentVersion(), OrderAction.PICK_UP);
                        shipments.recordPickedUp(order, original.expectedShipmentVersion());
                        production.completeShipperTransition(order, original.expectedOrderVersion(), original.expectedShipmentVersion(), OrderAction.PICK_UP);
                    }
                }
                if (hold) { held.countDown(); await(release); }
                return "OK";
            });
        } catch (ApplicationException e) { return e.errorCode().name(); }
        finally { SecurityContextHolder.clearContext(); }
    }

    private void observeOrderWait(int holder, int waiter, Race race, boolean opsFirst) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            var row = jdbc.queryForMap("SELECT wait_event_type,query FROM pg_stat_activity WHERE pid=?", waiter);
            var blockers = jdbc.queryForList("SELECT unnest(pg_blocking_pids(?))", Integer.class, waiter);
            if ("Lock".equals(row.get("wait_event_type")) && blockers.contains(holder)) {
                assertThat(row.get("query").toString()).containsIgnoringCase("orders");
                assertThat(row.get("query").toString()).containsIgnoringCase("for");
                System.out.println("ORD-OPS-01 production race=" + race + " opsFirst=" + opsFirst
                        + " waiter=" + waiter + " holder=" + holder + " blockers=" + blockers + " sql=" + row.get("query"));
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("Expected actual PostgreSQL Order blocker");
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
    private List<Map<String, Object>> shipmentAudits() {
        return jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) ORDER BY id", order);
    }
    private List<Map<String, Object>> assignmentHistory() {
        return jdbc.queryForList("SELECT * FROM uteexpress.shipment_assignment_history WHERE shipment_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) ORDER BY id", order);
    }
    private List<Object> snapshots() {
        var snapshot = new LinkedHashMap<>(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?", order));
        for (String field : List.of("status", "version", "updated_at", "ready_at", "cancelled_at", "cancellation_reason", "inventory_released_at")) snapshot.remove(field);
        return List.of(snapshot, jdbc.queryForList("SELECT * FROM uteexpress.order_items WHERE order_id=? ORDER BY id", order));
    }
    private List<List<Map<String, Object>>> state() {
        return List.of(jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.order_items WHERE order_id=? ORDER BY id", order), paymentState(),
                jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", order),
                jdbc.queryForList("SELECT * FROM uteexpress.products WHERE id=?", product),
                jdbc.queryForList("SELECT * FROM uteexpress.shipments WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='ORDER' AND target_id=? ORDER BY id", order),
                shipmentAudits(), assignmentHistory());
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
