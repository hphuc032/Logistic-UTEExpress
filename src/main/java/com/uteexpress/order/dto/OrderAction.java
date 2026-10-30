package com.uteexpress.order.dto;

/** Business intentions, not security roles. Guards are specified in docs/order-contracts.md. */
public enum OrderAction {
    CONFIRM(OrderStatus.NEW, OrderStatus.CONFIRMED),
    CANCEL_NEW(OrderStatus.NEW, OrderStatus.CANCELLED),
    EXPIRE_PAYMENT(OrderStatus.NEW, OrderStatus.CANCELLED),
    PICK_UP(OrderStatus.CONFIRMED, OrderStatus.PICKED_UP),
    CANCEL_CONFIRMED(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
    START_SHIPPING(OrderStatus.PICKED_UP, OrderStatus.SHIPPING),
    DELIVER(OrderStatus.SHIPPING, OrderStatus.DELIVERED),
    CANCEL_FAILED_DELIVERY(OrderStatus.SHIPPING, OrderStatus.CANCELLED),
    REQUEST_RETURN(OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED),
    REJECT_RETURN(OrderStatus.RETURN_REQUESTED, OrderStatus.DELIVERED),
    RECEIVE_RETURN(OrderStatus.RETURN_REQUESTED, OrderStatus.RETURNED),
    COMPLETE_REFUND(OrderStatus.RETURNED, OrderStatus.REFUNDED);

    private final OrderStatus from;
    private final OrderStatus to;

    OrderAction(OrderStatus from, OrderStatus to) {
        this.from = from;
        this.to = to;
    }

    public OrderStatus from() { return from; }
    public OrderStatus to() { return to; }
}
