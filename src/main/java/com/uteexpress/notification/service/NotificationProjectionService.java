package com.uteexpress.notification.service;

import com.uteexpress.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Rebuildable inbox projection; does not write Order or require changes to its publisher. */
@Service
public class NotificationProjectionService {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationProjectionService.class);
    private final NotificationRepository repository;
    private final NotificationCommitPublisher hints;
    private final ObjectProvider<PlatformTransactionManager> managers;
    public NotificationProjectionService(NotificationRepository repository, ObjectProvider<PlatformTransactionManager> managers, NotificationCommitPublisher hints) {
        this.repository = repository; this.managers = managers; this.hints = hints;
    }
    public int projectOrder(Long orderId) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("Positive Order ID required");
        var tx = new TransactionTemplate(managers.getObject());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> {
            if (!repository.lockProjectionOrder(orderId)) return 0;
            var inserted = repository.projectOrderHistory(orderId);
            inserted.forEach(hints::register);
            return inserted.size();
        });
    }
    public int reconcileBatch() {
        int inserted = 0;
        for (Long id : repository.pendingOrders()) {
            try {
                inserted += projectOrder(id);
            } catch (RuntimeException failure) {
                // projectOrder has already rolled back its own transaction. Other Orders can progress.
                LOG.warn("Notification projection failed for Order {}; committed history remains retryable", id, failure);
            }
        }
        return inserted;
    }
}
