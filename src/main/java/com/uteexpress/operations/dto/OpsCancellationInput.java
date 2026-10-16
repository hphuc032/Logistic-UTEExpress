package com.uteexpress.operations.dto;

import com.uteexpress.order.dto.OpsCancellationReason;
import com.uteexpress.order.dto.OpsShipmentExpectation;
import com.uteexpress.order.dto.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** Expected persisted facts only; no actor, owner, target status or arbitrary reason text. */
public record OpsCancellationInput(@NotNull OrderStatus expectedOrderStatus,
        @NotNull @PositiveOrZero Long expectedOrderVersion,
        @NotNull OpsShipmentExpectation expectedShipment,
        @PositiveOrZero Long expectedShipmentVersion, @NotNull OpsCancellationReason reasonCode) { }
