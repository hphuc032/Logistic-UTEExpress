package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.shipping.dto.ShipmentStatus;
import com.uteexpress.shipping.service.ShipmentFulfillmentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Order-internal guards, invoked only inside the lifecycle bean's SHIPPER/MANDATORY boundary. */
final class ShipperOrderAuthority {
    private final OrderRepository orders;
    private final EntityManager entityManager;
    private final ShipmentFulfillmentService shipments;
    private final PaymentReadService payments;

    ShipperOrderAuthority(OrderRepository orders, EntityManager entityManager,
            ShipmentFulfillmentService shipments, PaymentReadService payments) {
        this.orders = orders;
        this.entityManager = entityManager;
        this.shipments = shipments;
        this.payments = payments;
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
                || (action == OrderAction.DELIVER && (facts.pickedUpAt() == null || facts.attemptCount() != 1)))) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        OrderTransitionPolicy.requireTarget(order.getStatus(), action);
        if (completion && action == OrderAction.DELIVER) {
            payments.requireCollectedCodForDelivery(orderId, order.getGrandTotal());
        }
        return new GuardedTransition(order, facts.shipperId());
    }

    record GuardedTransition(Order order, Long actorId) { }
}
