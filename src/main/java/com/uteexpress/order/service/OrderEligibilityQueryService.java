package com.uteexpress.order.service;

/** TD -> QD review boundary; no persistence/query implementation in ORD-00. */
public interface OrderEligibilityQueryService {
    /**
     * Resolve the buyer via SEC-01 CurrentUserProvider. Check item belongs to that buyer's
     * order and order is DELIVERED. QD separately enforces unique review/order_item.
     * No caller-supplied buyer ID; unavailable or foreign items must not leak ownership.
     */
    boolean isCurrentBuyerEligibleToReview(Long orderItemId);
}
