package com.uteexpress.order.service;

import com.uteexpress.order.dto.ReviewableOrderItem;
import java.util.function.Function;

/** TD-owned transaction coordinator for QD's future Review creation. */
public interface OrderReviewWriteBoundary {
    /**
     * Call without an existing transaction. Resolves the principal, locks Order then
     * active buyer Account, rechecks ownership/DELIVERED and invokes write synchronously.
     * All locks survive callback return through commit. The callback must join this
     * transaction (REQUIRED/MANDATORY), acquire Review locks only afterwards, and must
     * neither suspend it, start asynchronous work nor call another Order lifecycle.
     * Exceptions escape and roll back all joined writes. No Review schema is owned here.
     * Missing/foreign/invalid items: RESOURCE_NOT_FOUND; other Order states: CONFLICT;
     * unauthenticated: UNAUTHENTICATED; invalid account/role: ACCESS_DENIED;
     * existing transaction or null callback: INVALID_REQUEST.
     */
    <T> T withEligibleCurrentBuyerItem(Long orderItemId, Function<ReviewableOrderItem, T> write);
}
