package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.shipping.dto.DeliveryAttemptOperation;
import com.uteexpress.shipping.service.ShipmentFulfillmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests the independent Order guard, not Shipment authority or successful SHIP-03 integration. */
class ShipmentOnlyOrderGuardTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final EntityManager entityManager = mock(EntityManager.class);
    final ShipmentFulfillmentService shipments = mock(ShipmentFulfillmentService.class);
    final PaymentReadService payments = mock(PaymentReadService.class);
    final Order order = mock(Order.class);
    final ShipperOrderAuthority authority = new ShipperOrderAuthority(orders, entityManager, shipments, payments);

    @BeforeEach void shippingOrder() {
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(OrderStatus.SHIPPING);
        when(order.getVersion()).thenReturn(7L);
        when(order.getReadyAt()).thenReturn(Instant.parse("2026-10-18T00:00:00Z"));
    }

    @ParameterizedTest @EnumSource(DeliveryAttemptOperation.class)
    void validOrderIsLockedAndRefreshedBeforeReadingLifecycleFacts(DeliveryAttemptOperation operation) {
        authority.requireShipmentOnlyOrder(10L, 7L, 3L, operation);
        var ordered = inOrder(orders, entityManager, order);
        ordered.verify(orders).findByIdForUpdate(10L);
        ordered.verify(entityManager).refresh(order, LockModeType.PESSIMISTIC_WRITE);
        ordered.verify(order).getStatus();
        ordered.verify(order).getVersion();
        verifyNoMoreInteractions(orders, entityManager);
        verifyNoInteractions(shipments, payments);
    }

    @ParameterizedTest @MethodSource("invalidTokens")
    void invalidInputCannotAcquireAnyLocks(Long id, Long ov, Long sv, DeliveryAttemptOperation operation) {
        fails(ErrorCode.INVALID_REQUEST, () -> authority.requireShipmentOnlyOrder(id, ov, sv, operation));
        verifyNoInteractions(orders, entityManager, shipments, payments, order);
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(DeliveryAttemptOperation.values()).flatMap(operation -> Stream.of(
                Arguments.of(null, 7L, 3L, operation), Arguments.of(0L, 7L, 3L, operation),
                Arguments.of(-1L, 7L, 3L, operation), Arguments.of(10L, null, 3L, operation),
                Arguments.of(10L, -1L, 3L, operation), Arguments.of(10L, Long.MAX_VALUE, 3L, operation),
                Arguments.of(10L, 7L, null, operation), Arguments.of(10L, 7L, -1L, operation),
                Arguments.of(10L, 7L, Long.MAX_VALUE, operation), Arguments.of(10L, 7L, 3L, null)));
    }

    @ParameterizedTest @EnumSource(DeliveryAttemptOperation.class)
    void zeroAndLargestNonOverflowTokensAreValidOrderInputs(DeliveryAttemptOperation operation) {
        for (long version : new long[] {0L, Long.MAX_VALUE - 1}) {
            when(order.getVersion()).thenReturn(version);
            authority.requireShipmentOnlyOrder(10L, version, version, operation);
        }
        verifyNoInteractions(shipments, payments);
    }

    @ParameterizedTest @EnumSource(DeliveryAttemptOperation.class)
    void missingOrderCannotReachShipping(DeliveryAttemptOperation operation) {
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.empty());
        fails(ErrorCode.RESOURCE_NOT_FOUND, () -> authority.requireShipmentOnlyOrder(10L, 7L, 3L, operation));
        verifyNoInteractions(entityManager, shipments, payments, order);
    }

    @ParameterizedTest @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "SHIPPING")
    void allOtherOrderStatusesConflict(OrderStatus status) {
        when(order.getStatus()).thenReturn(status);
        for (var operation : DeliveryAttemptOperation.values()) {
            fails(ErrorCode.CONFLICT, () -> authority.requireShipmentOnlyOrder(10L, 7L, 3L, operation));
        }
        verifyNoInteractions(shipments, payments);
    }

    @ParameterizedTest @ValueSource(strings = {"version", "ready", "delivered", "cancelled", "reason", "inventory"})
    void ineligibleOrderFactsConflict(String invalidFact) {
        switch (invalidFact) {
            case "version" -> when(order.getVersion()).thenReturn(8L);
            case "ready" -> when(order.getReadyAt()).thenReturn(null);
            case "delivered" -> when(order.getDeliveredAt()).thenReturn(Instant.EPOCH);
            case "cancelled" -> when(order.getCancelledAt()).thenReturn(Instant.EPOCH);
            case "reason" -> when(order.getCancellationReason()).thenReturn("Cancelled");
            case "inventory" -> when(order.getInventoryReleasedAt()).thenReturn(Instant.EPOCH);
            default -> throw new AssertionError(invalidFact);
        }
        for (var operation : DeliveryAttemptOperation.values()) {
            fails(ErrorCode.CONFLICT, () -> authority.requireShipmentOnlyOrder(10L, 7L, 3L, operation));
        }
        verifyNoInteractions(shipments, payments);
    }

    @Test void refreshedVersionOverridesEarlierManagedSnapshot() {
        doAnswer(invocation -> {
            when(order.getVersion()).thenReturn(8L);
            return null;
        }).when(entityManager).refresh(order, LockModeType.PESSIMISTIC_WRITE);
        fails(ErrorCode.CONFLICT, () -> authority.requireShipmentOnlyOrder(10L, 7L, 3L,
                DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE));
        verifyNoInteractions(shipments, payments);
    }

    private static void fails(ErrorCode code, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(code));
    }
}
