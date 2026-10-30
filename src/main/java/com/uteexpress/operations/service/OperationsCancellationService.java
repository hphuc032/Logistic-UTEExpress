package com.uteexpress.operations.service;

import com.uteexpress.operations.dto.OpsCancellationInput;
import com.uteexpress.order.dto.OpsOrderCancellationCommand;
import com.uteexpress.order.dto.OrderStatusChangedEvent;
import com.uteexpress.order.service.OrderLifecycleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** No outer transaction: the Order-owned boundary rejects one and opens its own transaction. */
@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
public class OperationsCancellationService {
    private final OrderLifecycleService lifecycle;
    public OperationsCancellationService(OrderLifecycleService lifecycle) { this.lifecycle = lifecycle; }

    public OrderStatusChangedEvent cancel(Long orderId, OpsCancellationInput input) {
        return lifecycle.cancelForOps(new OpsOrderCancellationCommand(orderId, input.expectedOrderStatus(),
                input.expectedOrderVersion(), input.expectedShipment(), input.expectedShipmentVersion(), input.reasonCode()));
    }
}
