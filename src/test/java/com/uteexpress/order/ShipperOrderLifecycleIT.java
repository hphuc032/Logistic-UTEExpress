package com.uteexpress.order;

import com.uteexpress.checkout.dto.*;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.service.AccountGovernanceService;
import com.uteexpress.governance.service.RoleGovernanceService;
import com.uteexpress.governance.dto.ManagedRole;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.payment.dto.CodCollectionCommand;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.payment.service.PaymentService;
import com.uteexpress.promotion.service.VoucherService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.shipping.service.ShipmentAssignmentService;
import com.uteexpress.shipping.service.ShipmentFulfillmentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest @Testcontainers @RecordApplicationEvents
class ShipperOrderLifecycleIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired ShipmentAssignmentService assignments;
    @Autowired ShipmentFulfillmentService shipments;
    @Autowired PaymentService payments;
    @MockitoSpyBean PaymentReadService paymentReads;
    @MockitoSpyBean VoucherService vouchers;
    @Autowired AccountGovernanceService accounts;
    @Autowired RoleGovernanceService roles;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationEvents events;
    long order, vendor, shipper, other, admin, product, buyer;
    CheckoutQuote baseQuote;
    TransactionTemplate tx;
    static final BigDecimal TOTAL = new BigDecimal("115000.00");

    @BeforeEach void setup() {
        tx = new TransactionTemplate(transactionManager);
        vendor = user("VENDOR"); shipper = user("SHIPPER"); other = user("SHIPPER"); admin = user("ADMIN");
        buyer = user("USER");
        long shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, vendor, UUID.randomUUID().toString());
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id", Long.class, UUID.randomUUID().toString());
        product = jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',100000,8,'ACTIVE') RETURNING id", Long.class, shop, category);
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id", Long.class, "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 18).toUpperCase());
        var quote = new CheckoutQuote(shop, List.of(new CheckoutQuote.ItemSnapshot(product, "Snapshot", new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("100000"), 1, new BigDecimal("100000"))),
                new CheckoutQuote.AddressSnapshot("Receiver", "0900000000", "VN", "District", "Address"),
                OrderTotals.calculate(new BigDecimal("100000"), BigDecimal.ZERO, new BigDecimal("15000")),
                provider, "STANDARD", null, BigDecimal.ZERO, BigDecimal.ZERO);
        baseQuote = quote;
        createOrder(quote);
        readyAndAssign();
        auth(shipper, "SHIPPER"); events.clear();
    }

    private void createOrder(CheckoutQuote quote) {
        tx.executeWithoutResult(s -> {
            var entity = orders.saveAndFlush(new Order(buyer, "ORD-" + UUID.randomUUID(), UUID.randomUUID().toString(), "hash", quote, Instant.now()));
            order = entity.getId();
            items.saveAllAndFlush(quote.items().stream().map(i -> new OrderItem(order, i)).toList());
            history.saveAndFlush(new OrderStatusHistory(order, null, OrderStatus.NEW, buyer, entity.getCreatedAt(), null));
            if (quote.voucher() != null) vouchers.recordUsage(quote.voucher(), order);
            payments.initializeCodForNewOrder(order);
        });
    }

    private void readyAndAssign() {
        auth(vendor, "VENDOR");
        lifecycle.transition(new OrderTransitionCommand(order, OrderStatus.NEW, 0L, OrderAction.CONFIRM, null));
        lifecycle.markReady(new OrderReadyCommand(order, 1L));
        auth(admin, "ADMIN"); assignments.assign(order, shipper, 2L);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void allThreeTransitionsUseOriginalVersionsHistoryAndAfterCommitEvents() {
        for (var action : List.of(OrderAction.PICK_UP, OrderAction.START_SHIPPING, OrderAction.DELIVER)) {
            long ov = version(), sv = shipmentVersion();
            long eventCount = events.stream(OrderStatusChangedEvent.class).count();
            tx.executeWithoutResult(s -> {
                lifecycle.prepareShipperTransition(order, ov, sv, action);
                assertThat(version()).isEqualTo(ov);
                assertThat(shipmentVersion()).isEqualTo(sv);
                if (action == OrderAction.DELIVER) payments.collectCod(new CodCollectionCommand(order, TOTAL));
                mutate(action, sv);
                var event = lifecycle.completeShipperTransition(order, ov, sv, action);
                assertThat(event.actorId()).isEqualTo(shipper);
                assertThat(event.fromStatus()).isEqualTo(action.from());
                assertThat(event.toStatus()).isEqualTo(action.to());
                assertThat(event.reason()).isNull();
                assertThat(events.stream(OrderStatusChangedEvent.class).count()).isEqualTo(eventCount);
            });
            assertThat(version()).isEqualTo(ov + 1);
            assertThat(shipmentVersion()).isEqualTo(sv + 1);
            assertThat(status()).isEqualTo(action.to().name());
            assertThat(shipmentStatus()).isEqualTo(action.to().name());
            assertThat(events.stream(OrderStatusChangedEvent.class).count()).isEqualTo(eventCount + 1);
        }
        assertThat(historyCount()).isEqualTo(5);
        assertThat(jdbc.queryForList("SELECT actor_id FROM uteexpress.order_status_history WHERE order_id=? AND to_status IN ('PICKED_UP','SHIPPING','DELIVERED')", Long.class, order)).containsOnly(shipper).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT delivered_at=updated_at FROM uteexpress.orders WHERE id=?", Boolean.class, order)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE order_id=?", String.class, order)).isEqualTo("PAID");
        assertThat(stock()).isEqualTo(8);
    }

    @Test void boundariesRequireExistingTransaction() {
        assertThatThrownBy(() -> lifecycle.prepareShipperTransition(order, 2L, 0L, OrderAction.PICK_UP))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> lifecycle.completeShipperTransition(order, 2L, 0L, OrderAction.PICK_UP))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> paymentReads.requireCollectedCodForDelivery(order, TOTAL))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    @ParameterizedTest @EnumSource(value = OrderAction.class, names = {"PICK_UP", "START_SHIPPING", "DELIVER"})
    void everyActionRejectsMissingSourceAndTargetEvidence(OrderAction action) {
        if (action != OrderAction.PICK_UP) perform(OrderAction.PICK_UP);
        if (action == OrderAction.DELIVER) perform(OrderAction.START_SHIPPING);
        long ov = version(), sv = shipmentVersion(); var before = state();
        rejects(() -> tx.executeWithoutResult(s -> {
            jdbc.update("UPDATE uteexpress.orders SET ready_at=NULL WHERE id=?", order);
            lifecycle.prepareShipperTransition(order, ov, sv, action);
        }), ErrorCode.CONFLICT);
        rejects(() -> tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, ov, sv, action);
            mutate(action, sv);
            jdbc.update("UPDATE uteexpress.shipments SET picked_up_at=NULL WHERE order_id=?", order);
            lifecycle.completeShipperTransition(order, ov, sv, action);
        }), ErrorCode.CONFLICT);
        rejects(() -> tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM uteexpress.shipment_assignment_history WHERE shipment_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?)", order);
            jdbc.update("DELETE FROM uteexpress.shipments WHERE order_id=?", order);
            lifecycle.prepareShipperTransition(order, ov, sv, action);
        }), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(state()).isEqualTo(before);
    }

    @Test void staleOrderShipmentAndIncrementedCompleteVersionsConflictWithoutEffects() {
        var before = state();
        rejects(() -> tx.executeWithoutResult(s -> lifecycle.prepareShipperTransition(order, 1L, 0L, OrderAction.PICK_UP)), ErrorCode.CONFLICT);
        rejects(() -> tx.executeWithoutResult(s -> lifecycle.prepareShipperTransition(order, 2L, 1L, OrderAction.PICK_UP)), ErrorCode.CONFLICT);
        rejects(() -> tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, 2L, 0L, OrderAction.PICK_UP);
            shipments.recordPickedUp(order, 0L);
            lifecycle.completeShipperTransition(order, 2L, 1L, OrderAction.PICK_UP);
        }), ErrorCode.CONFLICT);
        rejects(() -> tx.executeWithoutResult(s -> {
            shipments.recordPickedUp(order, 0L);
            lifecycle.completeShipperTransition(order, 1L, 0L, OrderAction.PICK_UP);
        }), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        perform(OrderAction.PICK_UP);
        rejects(() -> tx.executeWithoutResult(s -> lifecycle.completeShipperTransition(order, 2L, 0L, OrderAction.PICK_UP)), ErrorCode.CONFLICT);
    }

    @Test void reassignmentAndPersistedAccountRoleAreRecheckedByBothBoundaries() {
        tx.executeWithoutResult(s -> lifecycle.prepareShipperTransition(order, 2L, 0L, OrderAction.PICK_UP));
        auth(admin, "ADMIN"); assignments.reassign(order, other, 0L);
        auth(shipper, "SHIPPER");
        for (boolean complete : List.of(false, true)) {
            rejects(() -> tx.executeWithoutResult(s -> boundary(complete, 2L, 1L, OrderAction.PICK_UP)), ErrorCode.RESOURCE_NOT_FOUND);
        }
        auth(other, "SHIPPER");
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?", other);
        rejects(() -> tx.executeWithoutResult(s -> boundary(false, 2L, 1L, OrderAction.PICK_UP)), ErrorCode.ACCESS_DENIED);
        rejects(() -> tx.executeWithoutResult(s -> boundary(true, 2L, 0L, OrderAction.PICK_UP)), ErrorCode.ACCESS_DENIED);
        jdbc.update("UPDATE uteexpress.users SET status='ACTIVE' WHERE id=?", other);
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?", other);
        rejects(() -> tx.executeWithoutResult(s -> boundary(false, 2L, 1L, OrderAction.PICK_UP)), ErrorCode.ACCESS_DENIED);
        rejects(() -> tx.executeWithoutResult(s -> boundary(true, 2L, 0L, OrderAction.PICK_UP)), ErrorCode.ACCESS_DENIED);
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "VENDOR", "ADMIN", "MANAGER"})
    void wrongPrincipalRolesCannotPrepareOrComplete(String role) {
        auth(shipper, role);
        for (boolean complete : List.of(false, true)) {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> boundary(complete, 2L, 0L, OrderAction.PICK_UP)))
                    .isInstanceOf(AccessDeniedException.class);
        }
        assertThat(version()).isEqualTo(2);
    }

    @Test void forgedPrincipalActionIdAndVersionsDoNotAuthorize() {
        var before = state();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("" + shipper, null, List.of(new SimpleGrantedAuthority("ROLE_SHIPPER"))));
        rejects(() -> tx.executeWithoutResult(s -> lifecycle.prepareShipperTransition(order, 2L, 0L, OrderAction.PICK_UP)), ErrorCode.UNAUTHENTICATED);
        auth(shipper, "SHIPPER");
        for (boolean complete : List.of(false, true)) {
            for (var action : Arrays.asList(null, OrderAction.CONFIRM, OrderAction.CANCEL_CONFIRMED, OrderAction.EXPIRE_PAYMENT)) {
                rejects(() -> tx.executeWithoutResult(s -> boundary(complete, 2L, 0L, action)), ErrorCode.INVALID_REQUEST);
            }
            for (var version : Arrays.asList((Long) null, -1L, Long.MAX_VALUE)) {
                rejects(() -> tx.executeWithoutResult(s -> boundary(complete, 2L, version, OrderAction.PICK_UP)), ErrorCode.INVALID_REQUEST);
            }
            rejects(() -> tx.executeWithoutResult(s -> boundary(complete, -1L, 0L, OrderAction.PICK_UP)), ErrorCode.INVALID_REQUEST);
        }
        rejects(() -> tx.executeWithoutResult(s -> lifecycle.prepareShipperTransition(-1L, 2L, 0L, OrderAction.PICK_UP)), ErrorCode.INVALID_REQUEST);
        rejects(() -> tx.executeWithoutResult(s -> lifecycle.prepareShipperTransition(Long.MAX_VALUE, 2L, 0L, OrderAction.PICK_UP)), ErrorCode.RESOURCE_NOT_FOUND);
        // The generic vendor path cannot become an alternative shipper entry point.
        assertThatThrownBy(() -> lifecycle.transition(new OrderTransitionCommand(order, OrderStatus.CONFIRMED, 2L, OrderAction.PICK_UP, "forged")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "timestamp", "attempt", "ready", "version_jump"})
    void completionRequiresPersistedShipmentEvidence(String kind) {
        var before = state();
        rejects(() -> tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, 2L, 0L, OrderAction.PICK_UP);
            if (!kind.equals("missing")) shipments.recordPickedUp(order, 0L);
            switch (kind) {
                case "timestamp" -> jdbc.update("UPDATE uteexpress.shipments SET picked_up_at=NULL WHERE order_id=?", order);
                case "attempt" -> jdbc.update("UPDATE uteexpress.shipments SET attempt_count=1 WHERE order_id=?", order);
                case "ready" -> jdbc.update("UPDATE uteexpress.orders SET ready_at=NULL WHERE id=?", order);
                case "version_jump" -> jdbc.update("UPDATE uteexpress.shipments SET version=2 WHERE order_id=?", order);
                default -> { }
            }
            lifecycle.completeShipperTransition(order, 2L, 0L, OrderAction.PICK_UP);
        }), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"unpaid", "mismatch", "duplicate", "online", "expired", "missing_paid_at", "missing_payment"})
    void deliveryRejectsInvalidCodEvidenceAndRollsBackShipment(String kind) {
        shipping();
        if (!kind.equals("unpaid") && !kind.equals("missing_payment")) {
            jdbc.update("UPDATE uteexpress.payments SET status='PAID',paid_at=CURRENT_TIMESTAMP WHERE order_id=?", order);
        }
        switch (kind) {
            case "mismatch" -> jdbc.update("UPDATE uteexpress.payments SET amount=1 WHERE order_id=?", order);
            case "duplicate" -> jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',115000,?)", order, UUID.randomUUID().toString());
            case "online" -> jdbc.update("UPDATE uteexpress.payments SET method='ONLINE' WHERE order_id=?", order);
            case "expired" -> jdbc.update("UPDATE uteexpress.payments SET expired_at=CURRENT_TIMESTAMP WHERE order_id=?", order);
            case "missing_paid_at" -> jdbc.update("UPDATE uteexpress.payments SET paid_at=NULL WHERE order_id=?", order);
            case "missing_payment" -> jdbc.update("DELETE FROM uteexpress.payments WHERE order_id=?", order);
            default -> { }
        }
        var before = state(); events.clear();
        rejects(() -> tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
            shipments.recordDelivered(order, 2L);
            lifecycle.completeShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
        }), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    @Test void wrongAmountDuplicateCollectionAndFailureAfterCompleteRollbackEverything() {
        shipping(); var before = state(); events.clear();
        rejects(() -> tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
            payments.collectCod(new CodCollectionCommand(order, BigDecimal.ONE));
        }), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        // Also reject the amount after provisional Shipment delivery, proving its audit rolls back.
        rejects(() -> tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
            shipments.recordDelivered(order, 2L);
            assertThat(shipmentStatus()).isEqualTo("DELIVERED");
            payments.collectCod(new CodCollectionCommand(order, BigDecimal.ONE));
        }), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        rejects(() -> tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
            payments.collectCod(new CodCollectionCommand(order, TOTAL));
            payments.collectCod(new CodCollectionCommand(order, TOTAL));
        }), ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
            payments.collectCod(new CodCollectionCommand(order, TOTAL));
            shipments.recordDelivered(order, 2L);
            lifecycle.completeShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
            // JDBC sees the real Payment/JPA and Shipment/Order writes in this outer transaction.
            assertThat(status()).isEqualTo("DELIVERED");
            assertThat(shipmentStatus()).isEqualTo("DELIVERED");
            assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE order_id=?", String.class, order))
                    .isEqualTo("PAID");
            assertThat(historyCount()).isEqualTo(5);
            assertThat(auditCount()).isEqualTo(4);
            assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
            throw new IllegalStateException("Failure after lifecycle completion");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    @Test void refreshedOrderAndPaymentIgnoreEarlierManagedSnapshots() {
        shipping();
        tx.executeWithoutResult(s -> {
            orders.findById(order).orElseThrow();
            paymentReads.recordsForOrder(order);
            jdbc.update("UPDATE uteexpress.orders SET version=version+1 WHERE id=?", order);
            rejects(() -> lifecycle.prepareShipperTransition(order, 4L, 2L, OrderAction.DELIVER), ErrorCode.CONFLICT);
            s.setRollbackOnly();
        });
        tx.executeWithoutResult(s -> {
            paymentReads.recordsForOrder(order);
            jdbc.update("UPDATE uteexpress.payments SET status='PAID',paid_at=CURRENT_TIMESTAMP WHERE order_id=?", order);
            lifecycle.prepareShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
            shipments.recordDelivered(order, 2L);
            lifecycle.completeShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
        });
        assertThat(status()).isEqualTo("DELIVERED");
    }

    @Test void vendorCancellationClosesAssignmentAndRestoresStockAtomically() {
        auth(vendor, "VENDOR");
        cancel();
        assertThat(status()).isEqualTo("CANCELLED");
        assertThat(shipmentStatus()).isEqualTo("CANCELLED");
        assertThat(version()).isEqualTo(3);
        assertThat(shipmentVersion()).isEqualTo(1);
        assertThat(stock()).isEqualTo(9);
        assertThat(historyCount()).isEqualTo(3);
        var before = state(); rejects(this::cancel, ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void inventoryFailureRollsBackShipmentClosureAndAudit() {
        jdbc.update("UPDATE uteexpress.products SET stock=2147483647 WHERE id=?", product);
        auth(vendor, "VENDOR"); var before = state();
        rejects(this::cancel, ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @Test void invalidVoucherUsageRollsBackAssignedShipmentStockAndAllLifecycleEvidence() {
        String code = "SAVE" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbc.update("""
                INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,
                    starts_at,ends_at,total_limit,per_user_limit,created_by)
                VALUES (?,'SHOP','FIXED',?,10000,0,CURRENT_TIMESTAMP-INTERVAL '1 day',
                    CURRENT_TIMESTAMP+INTERVAL '1 day',10,10,?)
                """, code, baseQuote.shopId(), vendor);
        auth(buyer, "USER");
        tx.executeWithoutResult(s -> {
            var voucher = vouchers.lockForCheckout(code, baseQuote.shopId(), baseQuote.totals().subtotal());
            createOrder(new CheckoutQuote(baseQuote.shopId(), baseQuote.items(), baseQuote.address(),
                    OrderTotals.calculate(baseQuote.totals().subtotal(), voucher.discountAmount(), baseQuote.totals().shippingFee()),
                    baseQuote.shippingProviderId(), baseQuote.shippingServiceSnapshot(), null,
                    BigDecimal.ZERO, BigDecimal.ZERO, voucher));
        });
        readyAndAssign();
        // Database-valid corruption rejected by releaseForCancellation's persisted Order/usage comparison.
        jdbc.update("UPDATE uteexpress.voucher_usages SET discount_amount=1 WHERE order_id=?", order);
        assertThat(jdbc.queryForObject("SELECT discount_total FROM uteexpress.orders WHERE id=?", BigDecimal.class, order))
                .isEqualByComparingTo("10000");
        var before = state();
        long originalShipmentVersion = shipmentVersion(), originalAuditCount = auditCount();
        int originalStock = stock();
        var releaseReached = new AtomicInteger();
        doAnswer(invocation -> {
            // Prove failure happens after these real provisional writes, rather than an earlier guard.
            assertThat(shipmentStatus()).isEqualTo("CANCELLED");
            assertThat(shipmentVersion()).isEqualTo(originalShipmentVersion + 1);
            assertThat(auditCount()).isEqualTo(originalAuditCount + 1);
            assertThat(stock()).isEqualTo(originalStock + 1);
            releaseReached.incrementAndGet();
            return invocation.callRealMethod();
        }).when(AopTestUtils.<VoucherService>getUltimateTargetObject(vouchers)).releaseForCancellation(order);
        events.clear();
        auth(vendor, "VENDOR");
        rejects(this::cancel, ErrorCode.CONFLICT);
        assertThat(releaseReached).hasValue(1);
        // Compare complete persisted rows, including versions, timestamps, histories, audits and usage.
        assertThat(state()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.voucher_usages WHERE order_id=?", String.class, order))
                .isEqualTo("REDEEMED");
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    @Test void concurrentFullDeliveriesWithIdenticalOriginalVersionsCommitExactlyOnce() throws Exception {
        shipping();
        long ov = version(), sv = shipmentVersion(), originalHistoryCount = historyCount(), originalAuditCount = auditCount();
        var originalHistory = jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", order);
        var originalAudits = shipmentAudits();
        var originalProduct = jdbc.queryForList("SELECT * FROM uteexpress.products WHERE id=?", product);
        var originalUsage = jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE order_id=? ORDER BY id", order);
        var originalAssignment = jdbc.queryForList("SELECT * FROM uteexpress.shipment_assignment_history WHERE shipment_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) ORDER BY id", order);
        long paymentId = jdbc.queryForObject("SELECT id FROM uteexpress.payments WHERE order_id=?", Long.class, order);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE id=?", String.class, paymentId)).isEqualTo("UNPAID");
        var collected = new AtomicInteger();
        var delivered = new AtomicInteger();
        var completed = new AtomicInteger();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        var pids = List.of(new CompletableFuture<Integer>(), new CompletableFuture<Integer>());
        var results = new ArrayList<Future<String>>();
        try {
            tx.executeWithoutResult(s -> {
                // Hold the actual row until both real delivery transactions are waiting on it.
                jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, order);
                for (var pid : pids) results.add(pool.submit(() -> {
                    auth(shipper, "SHIPPER");
                    try {
                        return tx.execute(worker -> {
                            orders.findById(order).orElseThrow(); // Both persistence contexts contain the original snapshot.
                            pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            await(start);
                            lifecycle.prepareShipperTransition(order, ov, sv, OrderAction.DELIVER);
                            payments.collectCod(new CodCollectionCommand(order, TOTAL));
                            collected.incrementAndGet();
                            shipments.recordDelivered(order, sv);
                            delivered.incrementAndGet();
                            lifecycle.completeShipperTransition(order, ov, sv, OrderAction.DELIVER);
                            completed.incrementAndGet();
                            return "OK";
                        });
                    } catch (ApplicationException e) { return e.errorCode().name(); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
                start.countDown();
                for (var pid : pids) awaitOrderBlock(await(pid));
            });
            assertThat(List.of(results.get(0).get(15, TimeUnit.SECONDS), results.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "CONFLICT");
        } finally { start.countDown(); pool.shutdownNow(); }
        assertThat(collected).hasValue(1);
        assertThat(delivered).hasValue(1);
        assertThat(completed).hasValue(1);
        assertThat(status()).isEqualTo("DELIVERED");
        assertThat(shipmentStatus()).isEqualTo("DELIVERED");
        assertThat(version()).isEqualTo(ov + 1);
        assertThat(shipmentVersion()).isEqualTo(sv + 1);
        assertThat(historyCount()).isEqualTo(originalHistoryCount + 1);
        assertThat(auditCount()).isEqualTo(originalAuditCount + 1);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? AND to_status<>'DELIVERED' ORDER BY id", order))
                .isEqualTo(originalHistory);
        assertThat(shipmentAudits().subList(0, originalAudits.size())).isEqualTo(originalAudits);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=? AND from_status='SHIPPING' AND to_status='DELIVERED' AND actor_id=?", Long.class, order, shipper)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) AND action='SHIPMENT_DELIVERED' AND actor_id=?", Long.class, order, shipper)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM uteexpress.payments WHERE order_id=?", Long.class, order)).containsExactly(paymentId);
        assertThat(jdbc.queryForObject("SELECT method='COD' AND status='PAID' AND amount=? AND paid_at IS NOT NULL AND expired_at IS NULL FROM uteexpress.payments WHERE id=?", Boolean.class, TOTAL, paymentId)).isTrue();
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.products WHERE id=?", product)).isEqualTo(originalProduct);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE order_id=? ORDER BY id", order)).isEqualTo(originalUsage);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.shipment_assignment_history WHERE shipment_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) ORDER BY id", order)).isEqualTo(originalAssignment);
    }

    /** Regression for the former final-evidence TOCTOU witness, for both governance restrictions. */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void fulfillmentAccountLockMakesAdminRestrictionWaitUntilOuterCommit(boolean revokeRole) throws Exception {
        shipping(); events.clear();
        var evidenceChecked = new CountDownLatch(1);
        var restrictionPid = new CompletableFuture<Integer>();
        var restrictionCommitted = new AtomicInteger();
        var guardReached = new AtomicInteger();
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> restriction = pool.submit(() -> {
                await(evidenceChecked);
                auth(admin, "ADMIN");
                try {
                    tx.executeWithoutResult(s -> {
                        jdbc.execute("SET LOCAL lock_timeout='10s'");
                        restrictionPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        restrictShipper(revokeRole);
                    });
                    restrictionCommitted.incrementAndGet();
                } finally { SecurityContextHolder.clearContext(); }
            });
            doAnswer(invocation -> {
                guardReached.incrementAndGet();
                evidenceChecked.countDown();
                awaitTransactionBlock(await(restrictionPid));
                assertThat(restrictionCommitted).hasValue(0);
                assertThat(restriction.isDone()).isFalse();
                assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.users WHERE id=?", String.class, shipper))
                        .isEqualTo("ACTIVE");
                assertThat(persistedShipperRole()).isTrue();
                return invocation.callRealMethod();
            }).when(AopTestUtils.<PaymentReadService>getUltimateTargetObject(paymentReads))
                    .requireCollectedCodForDelivery(eq(order), any(BigDecimal.class));
            tx.executeWithoutResult(s -> {
                perform(OrderAction.DELIVER);
                // All boundaries have returned, but the outer coordinator has not committed.
                awaitTransactionBlock(await(restrictionPid));
                assertThat(restrictionCommitted).hasValue(0);
                assertThat(restriction.isDone()).isFalse();
                assertThat(status()).isEqualTo("DELIVERED");
                assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
            });
            restriction.get(15, TimeUnit.SECONDS);
        } finally { evidenceChecked.countDown(); pool.shutdownNow(); }
        assertThat(guardReached).hasValue(1);
        assertThat(restrictionCommitted).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.users WHERE id=?", String.class, shipper))
                .isEqualTo(revokeRole ? "ACTIVE" : "LOCKED");
        assertThat(persistedShipperRole()).isEqualTo(!revokeRole);
        assertThat(jdbc.queryForObject("SELECT token_version FROM uteexpress.users WHERE id=?", Long.class, shipper)).isEqualTo(1);
        assertThat(status()).isEqualTo("DELIVERED");
        assertThat(shipmentStatus()).isEqualTo("DELIVERED");
        assertThat(version()).isEqualTo(5);
        assertThat(shipmentVersion()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.payments WHERE order_id=?", String.class, order)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=? AND to_status='DELIVERED' AND actor_id=?", Long.class, order, shipper)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) AND action='SHIPMENT_DELIVERED'", Long.class, order)).isEqualTo(1);
        assertThat(events.stream(OrderStatusChangedEvent.class).filter(e -> e.toStatus() == OrderStatus.DELIVERED)).hasSize(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void adminRestrictionCommitsBeforeEligibilityAndDeliveryFailsWithoutEffects(boolean revokeRole) throws Exception {
        shipping(); var before = state(); events.clear();
        var deliveryPid = new CompletableFuture<Integer>();
        var pool = Executors.newSingleThreadExecutor();
        var results = new ArrayList<Future<String>>();
        try {
            auth(admin, "ADMIN");
            tx.executeWithoutResult(s -> {
                restrictShipper(revokeRole);
                results.add(pool.submit(() -> {
                    // Keep the old authenticated SHIPPER principal to exercise persisted eligibility.
                    auth(shipper, "SHIPPER");
                    try {
                        return tx.execute(worker -> {
                            jdbc.execute("SET LOCAL lock_timeout='10s'");
                            orders.findById(order).orElseThrow();
                            deliveryPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            perform(OrderAction.DELIVER);
                            return "OK";
                        });
                    } catch (ApplicationException e) { return e.errorCode().name(); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
                // Fulfillment holds Order/Shipment and waits on the actual governance account lock.
                awaitTransactionBlock(await(deliveryPid));
                assertThat(results.getFirst().isDone()).isFalse();
            });
            assertThat(results.getFirst().get(15, TimeUnit.SECONDS)).isEqualTo("ACCESS_DENIED");
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.users WHERE id=?", String.class, shipper))
                .isEqualTo(revokeRole ? "ACTIVE" : "LOCKED");
        assertThat(persistedShipperRole()).isEqualTo(!revokeRole);
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    private void restrictShipper(boolean revokeRole) {
        if (revokeRole) roles.change(shipper, 0L, ManagedRole.SHIPPER, false);
        else accounts.setLocked(shipper, 0L, true);
    }

    private boolean persistedShipperRole() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM uteexpress.user_roles ur JOIN uteexpress.roles r ON r.id=ur.role_id
                    WHERE ur.user_id=? AND r.code='SHIPPER')
                """, Boolean.class, shipper));
    }

    @Test void historyPersistenceFailureRollsBackCollectedCodShipmentAndOrder() {
        shipping(); var before = state(); events.clear();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            jdbc.execute("ALTER TABLE uteexpress.order_status_history ADD CONSTRAINT ord05_test_history CHECK (order_id <> "
                    + order + " OR to_status <> 'DELIVERED') NOT VALID");
            lifecycle.prepareShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
            payments.collectCod(new CodCollectionCommand(order, TOTAL));
            shipments.recordDelivered(order, 2L);
            lifecycle.completeShipperTransition(order, 4L, 2L, OrderAction.DELIVER);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(state()).isEqualTo(before);
        assertThat(events.stream(OrderStatusChangedEvent.class)).isEmpty();
    }

    @Test void cancellationRejectsPhysicalPickupBeforeInventoryRestoration() {
        tx.executeWithoutResult(s -> shipments.recordPickedUp(order, 0L));
        auth(vendor, "VENDOR"); var before = state();
        rejects(this::cancel, ErrorCode.CONFLICT);
        assertThat(state()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancellationAndPickupSerializeOnActualOrderLockWithStaleManagedSnapshot(boolean pickupWins) throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        var workerPid = new CompletableFuture<Integer>();
        var losers = new ArrayList<Future<String>>();
        try {
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.orders WHERE id=? FOR UPDATE", Long.class, order);
                losers.add(pool.submit(() -> {
                    auth(pickupWins ? vendor : shipper, pickupWins ? "VENDOR" : "SHIPPER");
                    try {
                        return tx.execute(worker -> {
                            orders.findById(order).orElseThrow();
                            workerPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            if (pickupWins) cancel(); else perform(OrderAction.PICK_UP);
                            return "OK";
                        });
                    } catch (ApplicationException e) { return e.errorCode().name(); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
                int pid;
                try { pid = workerPid.get(10, TimeUnit.SECONDS); }
                catch (Exception e) { throw new AssertionError(e); }
                awaitOrderBlock(pid);
                auth(pickupWins ? shipper : vendor, pickupWins ? "SHIPPER" : "VENDOR");
                if (pickupWins) perform(OrderAction.PICK_UP); else cancel();
            });
            assertThat(losers.getFirst().get(15, TimeUnit.SECONDS)).isEqualTo("CONFLICT");
        } finally { pool.shutdownNow(); }
        assertThat(status()).isEqualTo(pickupWins ? "PICKED_UP" : "CANCELLED");
        assertThat(shipmentStatus()).isEqualTo(status());
        assertThat(stock()).isEqualTo(pickupWins ? 8 : 9);
        assertThat(historyCount()).isEqualTo(3);
        assertThat(version()).isEqualTo(3);
        assertThat(shipmentVersion()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(2);
    }

    private void awaitOrderBlock(int pid) {
        awaitTransactionBlock(pid);
    }
    private void awaitTransactionBlock(int pid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            // A second waiter can queue behind the first waiter's tuple lock. Follow the entire
            // blocker chain to prove both requests ultimately wait on our held Order row.
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    WITH RECURSIVE blockers(pid) AS (
                        SELECT unnest(pg_blocking_pids(?))
                        UNION SELECT unnest(pg_blocking_pids(pid)) FROM blockers
                    ) SELECT EXISTS(SELECT 1 FROM blockers WHERE pid=pg_backend_pid())
                    """, Boolean.class, pid))) return;
            if (Thread.currentThread().isInterrupted()) throw new AssertionError("Interrupted waiting for PostgreSQL lock");
            Thread.onSpinWait();
        }
        throw new AssertionError("Competing transaction must block on this transaction's PostgreSQL lock");
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).as("bounded synchronization wait").isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private static <T> T await(CompletableFuture<T> future) {
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private void shipping() { perform(OrderAction.PICK_UP); perform(OrderAction.START_SHIPPING); }
    private void perform(OrderAction action) {
        long ov = version(), sv = shipmentVersion();
        tx.executeWithoutResult(s -> {
            lifecycle.prepareShipperTransition(order, ov, sv, action);
            if (action == OrderAction.DELIVER) payments.collectCod(new CodCollectionCommand(order, TOTAL));
            mutate(action, sv);
            lifecycle.completeShipperTransition(order, ov, sv, action);
        });
    }
    private void mutate(OrderAction action, long sv) {
        switch (action) {
            case PICK_UP -> shipments.recordPickedUp(order, sv);
            case START_SHIPPING -> shipments.recordShipping(order, sv);
            case DELIVER -> shipments.recordDelivered(order, sv);
            default -> throw new AssertionError(action);
        }
    }
    private void boundary(boolean complete, Long ov, Long sv, OrderAction action) {
        if (complete) lifecycle.completeShipperTransition(order, ov, sv, action);
        else lifecycle.prepareShipperTransition(order, ov, sv, action);
    }
    private void cancel() { lifecycle.transition(new OrderTransitionCommand(order, OrderStatus.CONFIRMED, 2L, OrderAction.CANCEL_CONFIRMED, "UNABLE_TO_FULFILL")); }
    private long version() { return jdbc.queryForObject("SELECT version FROM uteexpress.orders WHERE id=?", Long.class, order); }
    private long shipmentVersion() { return jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?", Long.class, order); }
    private String status() { return jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order); }
    private String shipmentStatus() { return jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?", String.class, order); }
    private int stock() { return jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?", Integer.class, product); }
    private long historyCount() { return jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=?", Long.class, order); }
    private long auditCount() { return jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?)", Long.class, order); }
    private List<Object> state() {
        return List.of(jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.shipments WHERE order_id=?", order),
                jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=? ORDER BY id", order),
                jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id", order),
                jdbc.queryForList("SELECT * FROM uteexpress.products WHERE id=?", product), shipmentAudits(),
                jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE order_id=? ORDER BY id", order),
                jdbc.queryForList("SELECT * FROM uteexpress.shipment_assignment_history WHERE shipment_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) ORDER BY id", order));
    }
    private List<Map<String, Object>> shipmentAudits() {
        return jdbc.queryForList("SELECT * FROM uteexpress.audit_logs WHERE target_type='SHIPMENT' AND target_id=(SELECT id FROM uteexpress.shipments WHERE order_id=?) ORDER BY id", order);
    }
    private long user(String role) {
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long id = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test-only-password','ACTIVE') RETURNING id", Long.class, name + "@example.test", name + "@example.test", name, name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role);
        return id;
    }
    private static void auth(long id, String role) {
        var principal = new UteExpressPrincipal(id, "opaque", null, 0, List.of(new SimpleGrantedAuthority("ROLE_" + role)), true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
    private static void rejects(Runnable operation, ErrorCode code) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(code));
    }
}
