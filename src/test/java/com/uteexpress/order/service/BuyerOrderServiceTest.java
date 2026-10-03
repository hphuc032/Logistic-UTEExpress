package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.order.repository.OrderStatusHistoryRepository;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuyerOrderServiceTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderItemRepository items = mock(OrderItemRepository.class);
    final OrderStatusHistoryRepository history = mock(OrderStatusHistoryRepository.class);
    final PaymentReadService payments = mock(PaymentReadService.class);
    final CurrentAccountIdProvider accounts = mock(CurrentAccountIdProvider.class);
    final BuyerOrderService service = new BuyerOrderService(orders, items, history, payments, accounts);

    @BeforeEach void buyer() { when(accounts.currentAccountId()).thenReturn(Optional.of(7L)); }

    @Test void missingOrForeignOrderNeverReadsChildrenOrUsesGlobalLookup() {
        when(orders.findByIdAndBuyerId(99L, 7L)).thenReturn(Optional.empty());
        fails(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail(99L));
        verify(orders).findByIdAndBuyerId(99L, 7L);
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(items, history, payments);
    }

    @Test void missingPersistedIdentityCannotQueryOrders() {
        when(accounts.currentAccountId()).thenReturn(Optional.empty());
        fails(ErrorCode.UNAUTHENTICATED, () -> service.list(0, 20));
        fails(ErrorCode.UNAUTHENTICATED, () -> service.detail(1L));
        verifyNoInteractions(orders, items, history, payments);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100"})
    void rejectsInvalidRangesBeforeJpa(int page, int size) {
        fails(ErrorCode.VALIDATION_FAILED, () -> service.list(page, size));
        verifyNoInteractions(orders, items, history, payments);
    }

    @Test void invalidIdsAreSafeNotFoundWithoutAnyOrderRead() {
        fails(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail(null));
        fails(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail(0L));
        fails(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail(-1L));
        verifyNoInteractions(orders, items, history, payments);
    }

    private static void fails(ErrorCode code, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(code));
    }
}
