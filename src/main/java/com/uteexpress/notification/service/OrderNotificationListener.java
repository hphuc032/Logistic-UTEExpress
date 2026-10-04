package com.uteexpress.notification.service;

import com.uteexpress.notification.repository.NotificationRepository;
import com.uteexpress.order.dto.OrderStatusChangedEvent;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** ORD-01 publishes only after commit; this consumer deduplicates retries by event ID. */
@Component
public class OrderNotificationListener {
    private final NotificationRepository repository;
    private final NotificationService notifications;
    private final ObjectProvider<PlatformTransactionManager> transactionManager;

    public OrderNotificationListener(NotificationRepository repository,
            NotificationService notifications, ObjectProvider<PlatformTransactionManager> transactionManager) {
        this.repository = repository;
        this.notifications = notifications;
        this.transactionManager = transactionManager;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        Objects.requireNonNull(event);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager.getObject());
        // The order event is dispatched in afterCommit while the old resources remain bound.
        // A new transaction is required or the inbox insert may never commit.
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            String state = event.toStatus().name();
            for (Long recipientId : repository.orderRecipients(event.orderId())) {
                notifications.record(recipientId, event.orderId(), "ORDER_STATUS",
                        "Cập nhật đơn hàng", "Đơn hàng #" + event.orderId() + " chuyển sang " + state + ".",
                        "order-status:" + event.eventId() + ":" + recipientId);
            }
        });
    }
}
