package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.shipping.dto.*;
import com.uteexpress.shipping.service.ShipmentAttemptService;
import com.uteexpress.shipping.service.ShipmentFulfillmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentOnlyOperationGuardTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final EntityManager entityManager = mock(EntityManager.class);
    final ShipmentFulfillmentService shipments = mock(ShipmentFulfillmentService.class);
    final PaymentReadService payments = mock(PaymentReadService.class);
    final ShipmentAttemptService attempts = mock(ShipmentAttemptService.class);
    final Order order = mock(Order.class);
    final ShipperOrderAuthority authority = new ShipperOrderAuthority(orders, entityManager, shipments, payments, attempts);
    static final Instant PICKUP = Instant.parse("2026-10-18T00:00:00Z");

    record Scenario(DeliveryAttemptOperation operation, boolean completion, int attempt, long sourceVersion) { }
    static Stream<Scenario> scenarios() {
        return Stream.of(
                new Scenario(DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE, false, 1, 2),
                new Scenario(DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE, true, 1, 2),
                new Scenario(DeliveryAttemptOperation.RETRY_DELIVERY, false, 1, 3),
                new Scenario(DeliveryAttemptOperation.RETRY_DELIVERY, true, 2, 3),
                new Scenario(DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE, false, 2, 4),
                new Scenario(DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE, true, 2, 4));
    }
    static Stream<Scenario> evidenceScenarios() {
        return scenarios().filter(s -> s.completion() || s.operation() == DeliveryAttemptOperation.RETRY_DELIVERY);
    }

    @BeforeEach void setup() {
        when(orders.findByIdForUpdate(10L)).thenReturn(Optional.of(order));
        when(order.getStatus()).thenReturn(OrderStatus.SHIPPING);
        when(order.getVersion()).thenReturn(7L);
        when(order.getReadyAt()).thenReturn(PICKUP);
    }

    @ParameterizedTest @MethodSource("scenarios")
    void validatesSourceAndTargetWithOriginalTokensAfterOrderLock(Scenario scenario) {
        stub(scenario, facts(scenario));
        guard(scenario);
        var sequence = inOrder(orders, entityManager, order, attempts);
        sequence.verify(orders).findByIdForUpdate(10L);
        sequence.verify(entityManager).refresh(order, LockModeType.PESSIMISTIC_WRITE);
        sequence.verify(order).getStatus();
        sequence.verify(order).getVersion();
        if (scenario.completion()) sequence.verify(attempts).requireOperationEvidence(10L, scenario.sourceVersion(), scenario.operation());
        else sequence.verify(attempts).lockForOperation(10L, scenario.sourceVersion(), scenario.operation());
        verifyNoMoreInteractions(attempts, orders, entityManager);
        verifyNoInteractions(shipments, payments);
    }

    @ParameterizedTest @MethodSource("scenarios")
    void staleOrderNeverReachesShippingHooks(Scenario scenario) {
        when(order.getVersion()).thenReturn(8L);
        conflict(() -> guard(scenario));
        verifyNoInteractions(attempts, shipments, payments);
    }

    @ParameterizedTest @ValueSource(strings = {"null", "shipment", "id", "order", "actor", "status", "version", "jump", "attempt", "max", "pickup", "delivered"})
    void rejectsInvalidPersistedShipmentFacts(String defect) {
        scenarios().forEach(s -> {
            var valid = facts(s);
            var f = valid.shipment();
            var bad = new ShipmentDeliveryFacts(new ShipmentFulfillmentFacts(
                    defect.equals("id") ? null : f.shipmentId(), defect.equals("order") ? 99L : f.orderId(),
                    defect.equals("actor") ? 99L : f.shipperId(), defect.equals("status") ? ShipmentStatus.DELIVERED : f.status(),
                    defect.equals("version") ? f.version() - 1 : defect.equals("jump") ? f.version() + 1 : f.version(),
                    defect.equals("attempt") ? 3 : f.attemptCount(), defect.equals("max") ? 3 : f.maxAttempts(),
                    defect.equals("pickup") ? null : f.pickedUpAt(), defect.equals("delivered") ? PICKUP : null), valid.failure());
            if (defect.equals("null")) bad = null;
            if (defect.equals("shipment")) bad = new ShipmentDeliveryFacts(null, valid.failure());
            stub(s, bad);
            conflict(() -> guard(s));
        });
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "id", "shipment", "attempt", "actor", "version", "time", "before_pickup", "reason", "reference", "long_reason", "long_reference"})
    void rejectsEvidenceForWrongOperationAttemptOrIdentity(String defect) {
        evidenceScenarios().forEach(s -> {
            var valid = facts(s);
            var e = valid.failure();
            var bad = new DeliveryFailureEvidence(defect.equals("id") ? 0L : e.id(),
                    defect.equals("shipment") ? 99L : e.shipmentId(), defect.equals("attempt") ? 3 - e.attemptNumber() : e.attemptNumber(),
                    defect.equals("version") ? e.sourceShipmentVersion() + 1 : e.sourceShipmentVersion(),
                    defect.equals("actor") ? 99L : e.shipperId(),
                    defect.equals("reason") ? " " : defect.equals("long_reason") ? "x".repeat(1001) : e.failureReason(),
                    defect.equals("reference") ? null : defect.equals("long_reference") ? "x".repeat(256) : e.evidenceReference(),
                    defect.equals("time") ? null : defect.equals("before_pickup") ? PICKUP.minusSeconds(1) : e.recordedAt());
            stub(s, new ShipmentDeliveryFacts(valid.shipment(), defect.equals("missing") ? null : bad));
            conflict(() -> guard(s));
        });
    }

    @ParameterizedTest @ValueSource(ints = {1, 2})
    void sourceFailureRejectsAlreadyPresentEvidence(int attempt) {
        var s = new Scenario(DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE, false, attempt, 2);
        var f = facts(s).shipment();
        stub(s, new ShipmentDeliveryFacts(f, new DeliveryFailureEvidence(1L, 20L, attempt, 2L, 30L, "No answer", "PROOF", PICKUP)));
        conflict(() -> guard(s));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void retryRejectsWrongAttemptEvenWithinNormalRange(boolean completion) {
        var s = new Scenario(DeliveryAttemptOperation.RETRY_DELIVERY, completion, completion ? 1 : 2, 3);
        stub(s, facts(s));
        conflict(() -> guard(s));
    }

    private ShipmentDeliveryFacts facts(Scenario s) {
        boolean failure = s.operation() == DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE;
        var shipment = new ShipmentFulfillmentFacts(20L, 10L, 30L,
                failure == s.completion() ? ShipmentStatus.DELIVERY_FAILED : ShipmentStatus.SHIPPING,
                s.sourceVersion() + (s.completion() ? 1 : 0), s.attempt(), 2, PICKUP, null);
        var evidence = failure && !s.completion() ? null : new DeliveryFailureEvidence(1L, 20L,
                failure ? s.attempt() : 1, failure ? s.sourceVersion() : s.sourceVersion() - 1,
                30L, "No answer", "PROOF", PICKUP.plusSeconds(1));
        return new ShipmentDeliveryFacts(shipment, evidence);
    }
    private void stub(Scenario s, ShipmentDeliveryFacts facts) {
        if (s.completion()) when(attempts.requireOperationEvidence(10L, s.sourceVersion(), s.operation())).thenReturn(facts);
        else when(attempts.lockForOperation(10L, s.sourceVersion(), s.operation())).thenReturn(facts);
    }
    private void guard(Scenario s) {
        authority.requireShipmentOnlyOperation(10L, 7L, s.sourceVersion(), s.operation(), s.completion(), 30L);
    }
    private static void conflict(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
