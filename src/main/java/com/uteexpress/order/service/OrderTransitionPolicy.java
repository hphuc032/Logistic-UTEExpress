package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.order.dto.OrderStatus;

/** Pure graph check only. Passing it is NOT authorization or permission to write a status. */
public final class OrderTransitionPolicy {
    private OrderTransitionPolicy() { }

    public static OrderStatus requireTarget(OrderStatus current, OrderAction action) {
        if (current == null || action == null) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        if (current != action.from()) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        return action.to();
    }
}
