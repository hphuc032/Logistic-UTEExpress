package com.uteexpress.payment.service;

import com.uteexpress.payment.dto.CodCollectionCommand;
import com.uteexpress.payment.dto.PaymentRecordView;
import java.util.List;

public interface PaymentService {
    /** Trusted checkout only, after explicit COD selection and flushed NEW order creation. Joins its transaction. */
    PaymentRecordView initializeCodForNewOrder(Long orderId);

    /**
     * Trusted fulfillment only: lock Order then Shipment and verify current assignment in the same transaction.
     * Collect while Order is SHIPPING, before its lifecycle transition to DELIVERED; locks extend through commit.
     * Never bind this method to a public endpoint without an authoritative assignment integration.
     */
    PaymentRecordView collectCod(CodCollectionCommand command);

    /** Ownership and buyer-role authorization are resolved from server-side identity. Reads never initialize. */
    List<PaymentRecordView> recordsForBuyer(Long orderId);
}
