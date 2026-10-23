package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.dto.OrderTransitionCommand;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.order.repository.OrderStatusHistoryRepository;
import com.uteexpress.security.CurrentUser;
import com.uteexpress.security.CurrentUserProvider;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundTransitionGuardTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    private final CurrentUserProvider users = mock(CurrentUserProvider.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final AtomicBoolean reachedAuthorization = new AtomicBoolean();
    private final OrderLifecycleServiceImpl lifecycle = new OrderLifecycleServiceImpl(orders, items, history,
            users, transactions, events, Clock.systemUTC()) {
        @Override protected Authorization authorize(CurrentUser user, Order order, OrderTransitionCommand command) {
            reachedAuthorization.set(true);
            return new Authorization(1L, "Permissive integration must not bypass refund evidence");
        }
    };

    @ParameterizedTest @EnumSource(OrderStatus.class)
    void genericCompleteRefundRejectsEverySourceBeforeAnyTransactionReadWriteOrEvent(OrderStatus status) {
        conflict(status);
        assertThat(reachedAuthorization).isFalse();
        verifyNoInteractions(orders, items, history, users, transactions, events);
    }

    @Test void genericRefundDoesNotJoinAnExistingAccountHeldTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            conflict(OrderStatus.RETURNED);
            verifyNoInteractions(orders, items, history, users, transactions, events);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private void conflict(OrderStatus status) {
        assertThatThrownBy(() -> lifecycle.transition(new OrderTransitionCommand(1L, status, 0L,
                OrderAction.COMPLETE_REFUND, null))).isInstanceOfSatisfying(ApplicationException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }
}
