package com.uteexpress.notification;

import com.uteexpress.notification.dto.NotificationCommitted;
import com.uteexpress.notification.service.NotificationCommitPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationCommitPublisherTest {
    @AfterEach void clear() { TransactionSynchronizationManager.clear(); }

    @Test void rejectsMissingTransaction() {
        var events = mock(ApplicationEventPublisher.class);
        assertThatIllegalStateException().isThrownBy(() ->
                new NotificationCommitPublisher(events).register(new NotificationCommitted(1L, 2L)));
        verifyNoInteractions(events);
    }

    @Test void committedListenerFailureDoesNotFailSuccessfulBusinessOperation() {
        var events = mock(ApplicationEventPublisher.class);
        var event = new NotificationCommitted(1L, 2L);
        doThrow(new IllegalStateException("broker unavailable")).when(events).publishEvent(event);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        new NotificationCommitPublisher(events).register(event);
        verifyNoInteractions(events);
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCommit())).doesNotThrowAnyException();
        verify(events).publishEvent(event);
    }
}
