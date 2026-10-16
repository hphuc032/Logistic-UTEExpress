package com.uteexpress.notification.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.notification.dto.NotificationView;
import com.uteexpress.notification.repository.NotificationRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final CurrentAccountIdProvider accounts;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications,
            CurrentAccountIdProvider accounts, Clock clock) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationView> inbox() {
        return notifications.inbox(accountId());
    }

    @Transactional
    public void markRead(Long id) {
        if (id == null || id <= 0) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Long recipientId = accountId();
        if (!notifications.belongsTo(id, recipientId)) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        notifications.markRead(id, recipientId, clock.instant());
    }

    /** Trusted server-side caller only; joins the business transaction or fails closed. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long recipientId, Long orderId, String type, String title,
            String message, String dedupKey) {
        if (recipientId == null || recipientId <= 0 || isBlank(type) || isBlank(title)
                || isBlank(message) || isBlank(dedupKey) || type.length() > 64
                || title.length() > 160 || message.length() > 1000 || dedupKey.length() > 255) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        notifications.insert(recipientId, orderId, type, title, message, dedupKey, clock.instant());
    }

    private Long accountId() {
        return accounts.currentAccountId()
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
