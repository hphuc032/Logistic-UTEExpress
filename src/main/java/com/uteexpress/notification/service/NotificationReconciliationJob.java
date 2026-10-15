package com.uteexpress.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "uteexpress.notifications", name = "reconciliation-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationReconciliationJob {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationReconciliationJob.class);
    private final NotificationProjectionService projection;
    public NotificationReconciliationJob(NotificationProjectionService projection) { this.projection = projection; }
    @Scheduled(initialDelayString = "${uteexpress.notifications.reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${uteexpress.notifications.reconcile-delay-ms:30000}")
    public void reconcile() {
        try { projection.reconcileBatch(); }
        catch (RuntimeException failure) { LOG.warn("Notification reconciliation failed; committed history remains retryable", failure); }
    }
}
