package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.shipping.dto.DeliveryAttemptOperation;
import com.uteexpress.shipping.dto.DeliveryFailureEvidence;
import com.uteexpress.shipping.dto.ShipmentDeliveryFacts;
import com.uteexpress.shipping.dto.ShipmentStatus;
import com.uteexpress.shipping.service.ShipmentAttemptService;
import com.uteexpress.shipping.service.ShipmentFulfillmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Order-internal guards, invoked only inside the lifecycle bean's SHIPPER/MANDATORY boundary. */
final class ShipperOrderAuthority {
    private final OrderRepository orders;
    private final EntityManager entityManager;
    private final ShipmentFulfillmentService shipments;
    private final PaymentReadService payments;
    private final ShipmentAttemptService attempts;

    ShipperOrderAuthority(OrderRepository orders, EntityManager entityManager,
            ShipmentFulfillmentService shipments, PaymentReadService payments, ShipmentAttemptService attempts) {
        this.orders = orders;
        this.entityManager = entityManager;
        this.shipments = shipments;
        this.payments = payments;
        this.attempts = attempts;
    }

    GuardedTransition requireTransition(Long orderId, Long orderVersion, Long shipmentVersion,
            OrderAction action, boolean completion) {
        if (orderId == null || orderId <= 0 || orderVersion == null || orderVersion < 0
                || shipmentVersion == null || shipmentVersion < 0 || shipmentVersion == Long.MAX_VALUE
                || action == null || (action != OrderAction.PICK_UP
                && action != OrderAction.START_SHIPPING && action != OrderAction.DELIVER)) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        ShipmentStatus source = switch (action) {
            case PICK_UP -> ShipmentStatus.ASSIGNED;
            case START_SHIPPING -> ShipmentStatus.PICKED_UP;
            case DELIVER -> ShipmentStatus.SHIPPING;
            default -> throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        };
        ShipmentStatus target = switch (action) {
            case PICK_UP -> ShipmentStatus.PICKED_UP;
            case START_SHIPPING -> ShipmentStatus.SHIPPING;
            case DELIVER -> ShipmentStatus.DELIVERED;
            default -> throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        };
        // Shipping resolves the server actor and checks persisted active SHIPPER/current assignment
        // before state/version are exposed. No caller-created facts authorize this boundary.
        var facts = completion
                ? shipments.requireFulfillmentEvidence(orderId, shipmentVersion + 1, target)
                : shipments.lockAssignedForTransition(orderId, shipmentVersion, source);
        if (order.getStatus() != action.from() || !orderVersion.equals(order.getVersion())
                || order.getReadyAt() == null) throw new ApplicationException(ErrorCode.CONFLICT);
        if (!completion && (facts.maxAttempts() != 2 || facts.deliveredAt() != null
                || (action == OrderAction.PICK_UP && (facts.pickedUpAt() != null || facts.attemptCount() != 0))
                || (action == OrderAction.START_SHIPPING && (facts.pickedUpAt() == null || facts.attemptCount() != 0))
                || (action == OrderAction.DELIVER && (facts.pickedUpAt() == null
                || facts.attemptCount() < 1 || facts.attemptCount() > facts.maxAttempts())))) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        OrderTransitionPolicy.requireTarget(order.getStatus(), action);
        if (completion && action == OrderAction.DELIVER) {
            payments.requireCollectedCodForDelivery(orderId, order.getGrandTotal());
        }
        return new GuardedTransition(order, facts.shipperId());
    }

    record GuardedTransition(Order order, Long actorId) { }

    void requireShipmentOnlyOperation(Long orderId, Long orderVersion, Long shipmentVersion,
            DeliveryAttemptOperation operation, boolean completion, Long actorId) {
        requireShipmentOnlyOrder(orderId, orderVersion, shipmentVersion, operation);
        // Retain Order before Shipping locks Shipment then the persisted actor Account.
        // Both hooks take the ORIGINAL Shipment token and reload their own persisted facts.
        var facts = completion
                ? attempts.requireOperationEvidence(orderId, shipmentVersion, operation)
                : attempts.lockForOperation(orderId, shipmentVersion, operation);
        requireShipmentOnlyFacts(orderId, shipmentVersion, operation, completion, actorId, facts);
    }

    private static void requireShipmentOnlyFacts(Long orderId, long sourceVersion,
            DeliveryAttemptOperation operation, boolean completion, Long actorId, ShipmentDeliveryFacts result) {
        if (result == null || result.shipment() == null) throw new ApplicationException(ErrorCode.CONFLICT);
        var shipment = result.shipment();
        boolean failure = operation == DeliveryAttemptOperation.REPORT_DELIVERY_FAILURE;
        ShipmentStatus expectedStatus = failure == completion ? ShipmentStatus.DELIVERY_FAILED : ShipmentStatus.SHIPPING;
        if (shipment.shipmentId() == null || shipment.shipmentId() <= 0
                || !orderId.equals(shipment.orderId()) || actorId == null || actorId <= 0
                || !actorId.equals(shipment.shipperId()) || shipment.status() != expectedStatus
                || shipment.version() != sourceVersion + (completion ? 1 : 0)
                || shipment.maxAttempts() != 2 || shipment.pickedUpAt() == null || shipment.deliveredAt() != null
                || (failure ? shipment.attemptCount() < 1 || shipment.attemptCount() > 2
                        : shipment.attemptCount() != (completion ? 2 : 1))) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        if (failure && !completion) {
            if (result.failure() != null) throw new ApplicationException(ErrorCode.CONFLICT);
            return; // QD also checks absent current failure and retained attempt-1 evidence for attempt 2.
        }
        DeliveryFailureEvidence evidence = result.failure();
        int evidenceAttempt = failure ? shipment.attemptCount() : 1;
        long evidenceVersion = failure ? sourceVersion : sourceVersion - 1;
        if (evidence == null || evidence.id() == null || evidence.id() <= 0
                || !shipment.shipmentId().equals(evidence.shipmentId())
                || !actorId.equals(evidence.shipperId()) || evidence.attemptNumber() != evidenceAttempt
                || evidenceVersion < 0 || evidence.sourceShipmentVersion() != evidenceVersion
                || evidence.recordedAt() == null || evidence.recordedAt().isBefore(shipment.pickedUpAt())
                || evidence.failureReason() == null || evidence.failureReason().isBlank()
                || evidence.failureReason().length() > 1000
                || evidence.evidenceReference() == null || evidence.evidenceReference().isBlank()
                || evidence.evidenceReference().length() > 255) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    /**
     * Order-owned part of SHIP-03; followed by QD's persisted hook under this retained lock.
     */
    void requireShipmentOnlyOrder(Long orderId, Long orderVersion, Long shipmentVersion,
            DeliveryAttemptOperation operation) {
        if (orderId == null || orderId <= 0 || orderVersion == null || orderVersion < 0
                || orderVersion == Long.MAX_VALUE || shipmentVersion == null || shipmentVersion < 0
                || shipmentVersion == Long.MAX_VALUE || operation == null) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        switch (operation) {
            case REPORT_DELIVERY_FAILURE, RETRY_DELIVERY -> { }
            default -> throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        // A preceding caller read may have put an obsolete entity in this persistence context.
        // Refresh while holding Order before acquiring any Shipment or Account lock.
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        if (order.getStatus() != OrderStatus.SHIPPING || !orderVersion.equals(order.getVersion())
                || order.getReadyAt() == null || order.getDeliveredAt() != null
                || order.getCancelledAt() != null || order.getCancellationReason() != null
                || order.getInventoryReleasedAt() != null) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }
}
