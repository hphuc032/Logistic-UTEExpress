package com.uteexpress.fulfillment.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.fulfillment.dto.FulfillmentRequest;
import com.uteexpress.fulfillment.dto.FulfillmentResult;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.payment.dto.CodCollectionCommand;
import com.uteexpress.payment.service.PaymentService;
import com.uteexpress.shipping.service.ShipmentFulfillmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the single outer transaction; module authorities retain their own guards. */
@Service
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())")
public class ShipperFulfillmentService {
    private final OrderLifecycleService orders;
    private final ShipmentFulfillmentService shipments;
    private final PaymentService payments;
    public ShipperFulfillmentService(OrderLifecycleService orders,
            ShipmentFulfillmentService shipments, PaymentService payments) {
        this.orders = orders; this.shipments = shipments; this.payments = payments;
    }
    @Transactional
    public FulfillmentResult transition(Long orderId, OrderAction action, FulfillmentRequest request) {
        if (request == null || request.expectedOrderVersion() == null || request.expectedShipmentVersion() == null
                || (action != OrderAction.PICK_UP && action != OrderAction.START_SHIPPING && action != OrderAction.DELIVER)
                || (action == OrderAction.DELIVER && request.collectedAmount() == null)
                || (action != OrderAction.DELIVER && request.collectedAmount() != null))
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        long ov = request.expectedOrderVersion(), sv = request.expectedShipmentVersion();
        orders.prepareShipperTransition(orderId, ov, sv, action);
        if (action == OrderAction.DELIVER)
            payments.collectCod(new CodCollectionCommand(orderId, request.collectedAmount()));
        switch (action) {
            case PICK_UP -> shipments.recordPickedUp(orderId, sv);
            case START_SHIPPING -> shipments.recordShipping(orderId, sv);
            case DELIVER -> shipments.recordDelivered(orderId, sv);
            default -> throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        var event = orders.completeShipperTransition(orderId, ov, sv, action);
        return new FulfillmentResult(orderId, event.toStatus().name(), ov + 1, sv + 1);
    }
}
