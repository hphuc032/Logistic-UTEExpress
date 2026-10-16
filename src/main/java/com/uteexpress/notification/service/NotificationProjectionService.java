package com.uteexpress.notification.service;

import com.uteexpress.notification.repository.NotificationRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Rebuildable inbox projection; does not write Order or require changes to its publisher. */
@Service
public class NotificationProjectionService {
    private final NotificationRepository repository;
    private final ObjectProvider<PlatformTransactionManager> managers;
    public NotificationProjectionService(NotificationRepository repository, ObjectProvider<PlatformTransactionManager> managers) {
        this.repository = repository; this.managers = managers;
    }
    public int projectOrder(Long orderId) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("Positive Order ID required");
        var tx = new TransactionTemplate(managers.getObject());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> repository.lockProjectionOrder(orderId) ? repository.projectOrderHistory(orderId) : 0);
    }
    public int reconcileBatch() {
        int inserted = 0;
        for (Long id : repository.pendingOrders()) inserted += projectOrder(id);
        return inserted;
    }
}
