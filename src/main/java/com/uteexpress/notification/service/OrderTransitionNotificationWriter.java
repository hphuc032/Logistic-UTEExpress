package com.uteexpress.notification.service;

import com.uteexpress.notification.repository.NotificationRepository;
import com.uteexpress.order.dto.OrderTransitionPrepared;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** HP-approved I8 contract: inbox rows join the lifecycle transaction; only hints wait for commit. */
@Service
public class OrderTransitionNotificationWriter {
    private final NotificationRepository notifications;
    private final NotificationCommitPublisher hints;

    public OrderTransitionNotificationWriter(NotificationRepository notifications, NotificationCommitPublisher hints) {
        this.notifications = notifications;
        this.hints = hints;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void prepare(OrderTransitionPrepared event) {
        // The lifecycle already owns Order. No REQUIRES_NEW, second Order lock or swallowed failure.
        notifications.projectOrderHistory(event.orderId()).forEach(hints::register);
    }
}
