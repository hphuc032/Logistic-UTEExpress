package com.uteexpress.order.entity;

import com.uteexpress.checkout.dto.*;
import com.uteexpress.common.exception.*;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.order.service.OrderLifecycleServiceImpl;
import com.uteexpress.payment.dto.PaymentStatus;
import com.uteexpress.payment.entity.Payment;
import com.uteexpress.security.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDomainCoreTest {
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final TestTransactions manager = new TestTransactions();
    private OrderLifecycleServiceImpl lifecycle;
    private Order order;

    @BeforeEach
    void setup() {
        when(users.currentUser()).thenReturn(Optional.of(new CurrentUser("opaque-subject", Set.of(RoleCode.USER), Set.of())));
        when(orders.saveAndFlush(any())).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 1L);
                ReflectionTestUtils.setField(saved, "version", 0L);
            }
            return saved;
        });
        lifecycle = new OrderLifecycleServiceImpl(orders, items, history, users, manager, events, Clock.fixed(NOW, ZoneOffset.UTC)) {
            @Override protected Authorization authorize(CurrentUser user, Order target, OrderTransitionCommand command) {
                // Test-only trusted evidence, not a production subject-to-ID implementation.
                assertThat(user.subject()).isEqualTo("opaque-subject");
                if (!target.getBuyerId().equals(7L)) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
                return new Authorization(7L, "Verified test reason");
            }
            @Override protected Long authorizeCreation(CurrentUser user, CheckoutQuote quote) {
                return 7L;
            }
        };
        order = lifecycle.createNew("ORD-1", "key", "hash", quote());
        when(orders.findById(1L)).thenReturn(Optional.of(order));
        clearInvocations(orders, items, history, events);
        manager.committed = 0;
        manager.rolledBack = 0;
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }

    private static CheckoutQuote.ItemSnapshot item() {
        return new CheckoutQuote.ItemSnapshot(3L, "Original product name", money("100000"),
                money("20000"), money("80000"), 1, money("80000"));
    }

    private static CheckoutQuote quote() {
        return new CheckoutQuote(2L, List.of(item()),
                new CheckoutQuote.AddressSnapshot("Receiver", "0900000000", "79", "District", "Detail"),
                OrderTotals.calculate(money("80000"), money("10000"), money("5000")),
                4L, "STANDARD", null, money("10"), money("7000"));
    }

    private static OrderTransitionCommand command(OrderStatus state, long version, OrderAction action) {
        return new OrderTransitionCommand(1L, state, version, action, null);
    }

    private OrderTransitionCommand cancel() { return command(OrderStatus.NEW, 0, OrderAction.CANCEL_NEW); }

    private static void failsWith(ErrorCode code, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApplicationException.class,
                ex -> assertThat(ex.errorCode()).isEqualTo(code));
    }

    @Test void snapshotsReconcilePostPromotionSubtotalAndCommissionBase() {
        OrderItem snapshot = new OrderItem(1L, item());
        assertThat(snapshot.getUnitPrice()).isEqualByComparingTo("100000");
        assertThat(snapshot.getDiscountSnapshot()).isEqualByComparingTo("20000");
        assertThat(snapshot.getFinalUnitPrice()).isEqualByComparingTo("80000");
        assertThat(snapshot.getLineTotal()).isEqualByComparingTo(order.getSubtotal());
        assertThat(order.getDiscountTotal()).isEqualByComparingTo("10000");
        assertThat(order.getGrandTotal()).isEqualByComparingTo("75000");
        assertThat(order.getSubtotal().subtract(order.getDiscountTotal())).isEqualByComparingTo("70000");
        assertThat(order.getCommissionAmount()).isEqualByComparingTo("7000");
        assertThat(order.getReceiverName()).isEqualTo("Receiver");
        assertThat(order.getBuyerId()).isEqualTo(7L);
        assertThat(order.getShopId()).isEqualTo(2L);
        assertThat(order.getCommissionPolicyId()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
        // Only scalar snapshots: there is no live Product entity/price dependency or public setter.
        assertThat(Arrays.stream(OrderItem.class.getMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.startsWith("set"));
        assertThat(snapshot.getProductNameSnapshot()).isEqualTo("Original product name");
    }

    @Test void rejectsPrePromotionSubtotalEvenWhenGrandTotalWouldMatch() {
        CheckoutQuote q = quote();
        CheckoutQuote wrong = new CheckoutQuote(q.shopId(), q.items(), q.address(),
                OrderTotals.calculate(money("100000"), money("30000"), money("5000")),
                q.shippingProviderId(), q.shippingServiceSnapshot(), null, money("10"), money("7000"));
        failsWith(ErrorCode.VALIDATION_FAILED, () -> lifecycle.createNew("O2", "K2", "hash", wrong));
        verifyNoInteractions(history, events);
    }

    @Test void rejectsInvalidLineArithmeticAndFractionalMoney() {
        for (var bad : List.of(
                new CheckoutQuote.ItemSnapshot(3L, "Name", money("100000"), money("20000"), money("80000"), 2, money("80000")),
                new CheckoutQuote.ItemSnapshot(3L, "Name", money("100000"), money("20000"), money("100000"), 1, money("100000")),
                new CheckoutQuote.ItemSnapshot(3L, "Name", money("100.01"), money("0"), money("100.01"), 1, money("100.01")))) {
            failsWith(ErrorCode.VALIDATION_FAILED, () -> new OrderItem(1L, bad));
        }
    }

    @Test void creationWritesInitialHistoryUnderLifecycleAuthority() {
        lifecycle.createNew("O2", "K2", "hash", quote());
        verify(items).saveAllAndFlush(argThat(snapshots -> {
            var iterator = snapshots.iterator();
            var snapshot = iterator.next();
            return snapshot.getOrderId().equals(1L)
                    && snapshot.getLineTotal().compareTo(order.getSubtotal()) == 0 && !iterator.hasNext();
        }));
        var capture = org.mockito.ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getFromStatus()).isNull();
        assertThat(capture.getValue().getToStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(capture.getValue().getActorId()).isEqualTo(7L);
        verifyNoInteractions(events); // ORD-00 transition event requires a non-null fromStatus.
    }

    @Test void legalTransitionWritesExactlyOneHistoryAndDispatchesAfterCommit() {
        doAnswer(invocation -> {
            assertThat(manager.committed).isEqualTo(1);
            return null;
        }).when(events).publishEvent(any(Object.class));
        var event = lifecycle.transition(cancel());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(NOW);
        assertThat(order.getUpdatedAt()).isEqualTo(NOW);
        var capture = org.mockito.ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).saveAndFlush(capture.capture());
        var row = capture.getValue();
        assertThat(row.getOrderId()).isEqualTo(1L);
        assertThat(row.getFromStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(row.getToStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(row.getActorId()).isEqualTo(7L);
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
        assertThat(row.getReason()).isEqualTo(event.reason());
        assertThat(event.eventId()).isNotNull();
        verify(events).publishEvent(event);
    }

    @Test void itemPersistenceFailureRollsBackInitialAggregate() {
        when(items.saveAllAndFlush(any())).thenThrow(new IllegalStateException("item failure"));
        assertThatThrownBy(() -> lifecycle.createNew("O2", "K2", "hash", quote()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(manager.rolledBack).isEqualTo(1);
        verifyNoInteractions(history, events);
    }

    @Test void outerRollbackDoesNotDispatchEvent() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            lifecycle.transition(cancel());
            verifyNoInteractions(events);
            tx.setRollbackOnly();
        });
        assertThat(manager.rolledBack).isEqualTo(1);
        assertThat(manager.committed).isZero();
        verifyNoInteractions(events);
    }

    @Test void joinedTransactionWaitsForOuterCommit() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            lifecycle.transition(cancel());
            verifyNoInteractions(events);
        });
        verify(events).publishEvent(any(OrderStatusChangedEvent.class));
        assertThat(manager.committed).isEqualTo(1);
    }

    @Test void expectedStatusMismatchHasNoWrites() {
        failsWith(ErrorCode.CONFLICT, () -> lifecycle.transition(command(OrderStatus.CONFIRMED, 0, OrderAction.CANCEL_NEW)));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(history, events);
    }

    @Test void illegalEdgeHasNoWrites() {
        failsWith(ErrorCode.CONFLICT, () -> lifecycle.transition(command(OrderStatus.NEW, 0, OrderAction.DELIVER)));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(history, events);
    }

    @Test void staleVersionHasNoWrites() {
        failsWith(ErrorCode.CONFLICT, () -> lifecycle.transition(command(OrderStatus.NEW, 1, OrderAction.CANCEL_NEW)));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(history, events);
    }

    @Test void concurrentFlushConflictRollsBackWithoutHistoryOrEvent() {
        when(orders.saveAndFlush(order)).thenThrow(new OptimisticLockingFailureException("concurrent update"));
        failsWith(ErrorCode.CONFLICT, () -> lifecycle.transition(cancel()));
        assertThat(manager.rolledBack).isEqualTo(1);
        verifyNoInteractions(history, events);
    }

    @Test void historyFailureRollsBackAndDoesNotDispatchEvent() {
        when(history.saveAndFlush(any())).thenThrow(new IllegalStateException("history failure"));
        assertThatThrownBy(() -> lifecycle.transition(cancel())).isInstanceOf(IllegalStateException.class);
        assertThat(manager.rolledBack).isEqualTo(1);
        verifyNoInteractions(events);
    }

    @Test void missingIdentityAndForeignOwnershipFailClosed() {
        when(users.currentUser()).thenReturn(Optional.empty());
        failsWith(ErrorCode.UNAUTHENTICATED, () -> lifecycle.transition(cancel()));
        verifyNoInteractions(orders, history, events);
        when(users.currentUser()).thenReturn(Optional.of(new CurrentUser("opaque-subject", Set.of(RoleCode.USER), Set.of())));
        ReflectionTestUtils.setField(order, "buyerId", 99L);
        failsWith(ErrorCode.ACCESS_DENIED, () -> lifecycle.transition(cancel()));
        verifyNoInteractions(history, events);
    }

    @Test void defaultSkeletonDeniesWithoutIdentityAndBusinessIntegrations() {
        var closed = new OrderLifecycleServiceImpl(orders, items, history, users, manager, events, Clock.systemUTC());
        failsWith(ErrorCode.ACCESS_DENIED, () -> closed.transition(cancel()));
        failsWith(ErrorCode.ACCESS_DENIED, () -> closed.createNew("O2", "K2", "hash", quote()));
        failsWith(ErrorCode.ACCESS_DENIED, () -> closed.expireUnpaidOnlineOrder(1L, 0L));
        failsWith(ErrorCode.ACCESS_DENIED, () -> lifecycle.transition(command(OrderStatus.NEW, 0, OrderAction.EXPIRE_PAYMENT)));
        verifyNoInteractions(history, events);
    }

    @Test void validatesInternalCommands() {
        failsWith(ErrorCode.INVALID_REQUEST, () -> lifecycle.transition(null));
        failsWith(ErrorCode.INVALID_REQUEST, () -> lifecycle.transition(command(OrderStatus.NEW, -1, OrderAction.CANCEL_NEW)));
        verifyNoInteractions(orders, history, events);
    }

    @Test void uncheckedBrowserReasonDoesNotReachHistoryOrEvent() {
        var event = lifecycle.transition(new OrderTransitionCommand(1L, OrderStatus.NEW, 0L,
                OrderAction.CANCEL_NEW, "Untrusted address/payment text"));
        assertThat(event.reason()).isEqualTo("Verified test reason");
        var capture = org.mockito.ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(history).saveAndFlush(capture.capture());
        assertThat(capture.getValue().getReason()).isEqualTo("Verified test reason");
    }

    @Test void requiredApprovedReasonCannotBeBlank() {
        var blankReason = new OrderLifecycleServiceImpl(orders, items, history, users, manager, events, Clock.systemUTC()) {
            @Override protected Authorization authorize(CurrentUser user, Order target, OrderTransitionCommand command) {
                return new Authorization(7L, "  ");
            }
        };
        ReflectionTestUtils.setField(order, "status", OrderStatus.RETURN_REQUESTED);
        failsWith(ErrorCode.CONFLICT, () -> blankReason.transition(
                command(OrderStatus.RETURN_REQUESTED, 0, OrderAction.REJECT_RETURN)));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(history, events);
    }

    @Test void deliveredTimeIsPreservedOnReturnRejection() {
        Instant original = NOW.minusSeconds(3600);
        ReflectionTestUtils.setField(order, "status", OrderStatus.RETURN_REQUESTED);
        ReflectionTestUtils.setField(order, "deliveredAt", original);
        lifecycle.transition(command(OrderStatus.RETURN_REQUESTED, 0, OrderAction.REJECT_RETURN));
        assertThat(order.getDeliveredAt()).isEqualTo(original);
    }

    @Test void paymentUsesOnlyApprovedStatesAndAllowsMultipleAttempts() {
        assertThat(PaymentStatus.values()).containsExactly(PaymentStatus.UNPAID, PaymentStatus.PAID);
        Payment first = new Payment(1L, CheckoutRequest.PaymentMethod.COD, quote().totals(), "attempt-1", NOW);
        Payment second = new Payment(1L, CheckoutRequest.PaymentMethod.ONLINE, quote().totals(), "attempt-2", NOW);
        assertThat(first.getOrderId()).isEqualTo(second.getOrderId());
        assertThat(first.getAttemptKey()).isNotEqualTo(second.getAttemptKey());
        assertThat(first.getStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(first.getAmount()).isEqualTo(money("75000.00"));
        assertThat(first.getProviderReference()).isNull();
        assertThat(first.getPaidAt()).isNull();
        assertThat(first.getExpiredAt()).isNull();
        assertThat(first.getUpdatedAt()).isEqualTo(NOW);
        failsWith(ErrorCode.VALIDATION_FAILED,
                () -> new Payment(1L, CheckoutRequest.PaymentMethod.COD, quote().totals(), " ", NOW));
    }

    @Test void entityHasNoNestedApplicationService() {
        assertThat(Order.class.getDeclaredClasses()).isEmpty();
        assertThat(OrderLifecycleServiceImpl.class.getEnclosingClass()).isNull();
        assertThat(OrderLifecycleServiceImpl.class.getPackageName()).isEqualTo("com.uteexpress.order.service");
    }

    @Test void nullHumanActorCannotUseDeferredSystemBoundary() {
        var missingActor = new OrderLifecycleServiceImpl(orders, items, history, users, manager, events, Clock.systemUTC()) {
            @Override protected Authorization authorize(CurrentUser user, Order target, OrderTransitionCommand command) {
                return new Authorization(null, "Not a system authorization");
            }
            @Override protected Long authorizeCreation(CurrentUser user, CheckoutQuote quote) {
                return null;
            }
        };
        failsWith(ErrorCode.ACCESS_DENIED, () -> missingActor.transition(cancel()));
        failsWith(ErrorCode.ACCESS_DENIED, () -> missingActor.createNew("O2", "K2", "hash", quote()));
        failsWith(ErrorCode.ACCESS_DENIED, () -> missingActor.transition(
                command(OrderStatus.NEW, 0, OrderAction.EXPIRE_PAYMENT)));
        when(users.currentUser()).thenReturn(Optional.empty());
        failsWith(ErrorCode.ACCESS_DENIED, () -> missingActor.expireUnpaidOnlineOrder(1L, 0L));
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(history, events);
        // Domain history supports the future trusted system path; no live expiry path exists yet.
        assertThat(new OrderStatusHistory(1L, OrderStatus.NEW, OrderStatus.CANCELLED,
                null, NOW, "EXPIRE_PAYMENT").getActorId()).isNull();
    }

    @Test void historySupportsNullSystemActorAndCommandContainsNoActor() {
        var row = new OrderStatusHistory(1L, OrderStatus.NEW, OrderStatus.CANCELLED, null, NOW, "EXPIRE_PAYMENT");
        assertThat(row.getActorId()).isNull();
        assertThat(Arrays.stream(OrderTransitionCommand.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("orderId", "expectedStatus", "expectedVersion", "action", "reason");
    }

    @Test void mappingContractsUseIdentityVersionStringEnumsAndDecimalMoney() throws Exception {
        for (Class<?> type : List.of(Order.class, OrderItem.class, OrderStatusHistory.class, Payment.class)) {
            assertThat(type.getAnnotation(Entity.class)).isNotNull();
            assertThat(type.getDeclaredField("id").getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
            for (var field : type.getDeclaredFields()) {
                if (field.getType().equals(BigDecimal.class) && !field.getName().equals("commissionRateSnapshot")) {
                    assertThat(field.getAnnotation(Column.class).precision()).isEqualTo(19);
                    assertThat(field.getAnnotation(Column.class).scale()).isEqualTo(2);
                }
                if (field.getType().isEnum()) assertThat(field.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
            }
        }
        assertThat(Order.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
        assertThat(Order.class.getDeclaredField("version").getType()).isEqualTo(Long.class);
        assertThat(Modifier.isPrivate(Order.class.getDeclaredField("status").getModifiers())).isTrue();
        assertThat(Arrays.stream(Order.class.getMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.startsWith("set"));
        assertThat(Payment.class.getAnnotation(Table.class).uniqueConstraints())
                .extracting(UniqueConstraint::name).containsExactly("uq_payments_attempt_key", "uq_payments_provider_reference");
        assertThat(Order.class.getAnnotation(Table.class).uniqueConstraints())
                .extracting(UniqueConstraint::name).contains("uq_orders_buyer_id_checkout_key");
    }

    /** Exercises real Spring transaction participation/synchronizations, not database durability. */
    private static class TestTransactions extends AbstractPlatformTransactionManager {
        private boolean active;
        int committed;
        int rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object transaction) { return active; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { active = true; }
        @Override protected void doCommit(DefaultTransactionStatus status) { committed++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack++; }
        @Override protected void doCleanupAfterCompletion(Object transaction) { active = false; }
    }
}
