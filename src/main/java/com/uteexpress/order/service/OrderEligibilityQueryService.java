package com.uteexpress.order.service;

/** TD -> QD advisory review query. Never authorizes a later write transaction. */
public interface OrderEligibilityQueryService {
    /**
     * Resolve the buyer via SEC-01 CurrentAccountIdProvider and persisted USER/VENDOR
     * eligibility. Missing/foreign/invalid item IDs return false; owned items return true
     * only while their Order is DELIVERED. Missing authentication is UNAUTHENTICATED;
     * inactive or non-buyer accounts are ACCESS_DENIED. No locks or side effects.
     * Use OrderReviewWriteBoundary for writes. QD owns unique review/order_item.
     */
    boolean isCurrentBuyerEligibleToReview(Long orderItemId);
}
