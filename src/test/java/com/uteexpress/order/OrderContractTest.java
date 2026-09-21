package com.uteexpress.order;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.service.OrderTransitionPolicy;
import com.uteexpress.shipping.dto.ShipmentStatus;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class OrderContractTest {
    @Test
    void statusVocabulariesRemainSeparate() {
        assertThat(OrderStatus.values()).extracting(Enum::name).containsExactly(
                "NEW", "CONFIRMED", "PICKED_UP", "SHIPPING", "DELIVERED", "CANCELLED",
                "RETURN_REQUESTED", "RETURNED", "REFUNDED");
        assertThat(ShipmentStatus.values()).extracting(Enum::name).containsExactly(
                "ASSIGNED", "PICKED_UP", "SHIPPING", "DELIVERY_FAILED", "DELIVERED",
                "RETURNED_TO_SENDER", "CANCELLED");
        assertThatThrownBy(() -> OrderStatus.valueOf("DELIVERY_FAILED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyActionMatchesTheApprovedMatrixAndRejectsAllOtherSources() {
        Map<OrderAction, String> approved = Map.ofEntries(
                Map.entry(OrderAction.CONFIRM, "NEW/CONFIRMED"),
                Map.entry(OrderAction.CANCEL_NEW, "NEW/CANCELLED"),
                Map.entry(OrderAction.EXPIRE_PAYMENT, "NEW/CANCELLED"),
                Map.entry(OrderAction.PICK_UP, "CONFIRMED/PICKED_UP"),
                Map.entry(OrderAction.CANCEL_CONFIRMED, "CONFIRMED/CANCELLED"),
                Map.entry(OrderAction.START_SHIPPING, "PICKED_UP/SHIPPING"),
                Map.entry(OrderAction.DELIVER, "SHIPPING/DELIVERED"),
                Map.entry(OrderAction.CANCEL_FAILED_DELIVERY, "SHIPPING/CANCELLED"),
                Map.entry(OrderAction.REQUEST_RETURN, "DELIVERED/RETURN_REQUESTED"),
                Map.entry(OrderAction.REJECT_RETURN, "RETURN_REQUESTED/DELIVERED"),
                Map.entry(OrderAction.RECEIVE_RETURN, "RETURN_REQUESTED/RETURNED"),
                Map.entry(OrderAction.COMPLETE_REFUND, "RETURNED/REFUNDED"));
        assertThat(approved).hasSize(OrderAction.values().length);
        approved.forEach((action, pair) -> {
            String[] states = pair.split("/");
            for (OrderStatus source : OrderStatus.values()) {
                if (source.name().equals(states[0])) {
                    assertThat(OrderTransitionPolicy.requireTarget(source, action).name()).isEqualTo(states[1]);
                } else {
                    assertThatThrownBy(() -> OrderTransitionPolicy.requireTarget(source, action))
                            .isInstanceOfSatisfying(ApplicationException.class,
                                    ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFLICT));
                }
            }
        });
    }

    @Test
    void missingStateOrActionIsInvalidInput() {
        assertThatThrownBy(() -> OrderTransitionPolicy.requireTarget(null, OrderAction.CONFIRM))
                .isInstanceOfSatisfying(ApplicationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> OrderTransitionPolicy.requireTarget(OrderStatus.NEW, null))
                .isInstanceOf(ApplicationException.class);
    }
}
