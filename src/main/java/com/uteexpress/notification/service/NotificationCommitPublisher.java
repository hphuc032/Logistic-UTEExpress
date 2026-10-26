package com.uteexpress.notification.service;

import com.uteexpress.notification.dto.NotificationCommitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Best-effort hints only; listener failure cannot turn a committed write into an HTTP failure. */
@Component
public class NotificationCommitPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationCommitPublisher.class);
    private final ApplicationEventPublisher events;

    public NotificationCommitPublisher(ApplicationEventPublisher events) { this.events = events; }

    public void register(NotificationCommitted event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Notification hint requires a transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { events.publishEvent(event); }
                catch (RuntimeException failure) {
                    LOG.warn("Notification hint failed for inbox row {}; recover through inbox HTTP",
                            event.notificationId(), failure);
                }
            }
        });
    }
}
