package com.uteexpress.order.service;

import com.uteexpress.order.dto.OrderStatusChangedEvent;
import com.uteexpress.order.dto.OrderTransitionCommand;

/**
 * Sole authority for OrderStatus writes. No implementation in ORD-00.
 * Implementation MUST obtain identity from SEC-01 CurrentUserProvider, resolve the subject
 * to a persisted user, validate roles/ownership/scope and all guards in docs/order-contracts.md.
 * Validate expected status/version and trusted evidence again inside the write transaction;
 * update timestamps, append history and publish the result only after commit.
 */
public interface OrderLifecycleService {
    /** Authenticated callers only; EXPIRE_PAYMENT is rejected with ACCESS_DENIED. */
    OrderStatusChangedEvent transition(OrderTransitionCommand command);

    /**
     * Internal scheduler only, never exposed through a controller. Lock/reload the order
     * and payment attempts; only expired unpaid online NEW orders qualify. actor_id is null.
     * A late PAID callback must serialize with this operation. Stale/repeated calls conflict.
     */
    OrderStatusChangedEvent expireUnpaidOnlineOrder(Long orderId, Long expectedVersion);
}
