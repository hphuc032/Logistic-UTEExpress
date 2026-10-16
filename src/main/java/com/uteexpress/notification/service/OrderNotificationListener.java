package com.uteexpress.notification.service;

import com.uteexpress.order.dto.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** After-commit event is a wake-up hint; only persisted history creates inbox entries. */
@Component
public class OrderNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderNotificationListener.class);
    private final NotificationProjectionService projection;
    public OrderNotificationListener(NotificationProjectionService projection) { this.projection = projection; }
    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        try { projection.projectOrder(event.orderId()); }
        catch (RuntimeException failure) {
            // Order already committed: leave history as the retry source, never report a failed Order write.
            LOG.warn("Notification projection failed for Order {}; reconciliation will retry", event.orderId(), failure);
        }
    }
}
