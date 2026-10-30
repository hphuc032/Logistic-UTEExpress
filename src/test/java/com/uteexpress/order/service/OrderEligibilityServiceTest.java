package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.dto.AuthAccountSnapshot;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderEligibilityServiceTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderItemRepository items = mock(OrderItemRepository.class);
    final CurrentAccountIdProvider accounts = mock(CurrentAccountIdProvider.class);
    final IdentityAuthenticationService authentication = mock(IdentityAuthenticationService.class);
    final AccountIdentityService identities = mock(AccountIdentityService.class);
    final EntityManager entities = mock(EntityManager.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final StaticListableBeanFactory persistence = new StaticListableBeanFactory(Map.of(
            "orders", orders, "items", items, "entities", entities, "manager", manager));
    final OrderEligibilityService service = new OrderEligibilityService(
            persistence.getBeanProvider(OrderRepository.class), persistence.getBeanProvider(OrderItemRepository.class),
            accounts, authentication, identities, persistence.getBeanProvider(EntityManager.class),
            persistence.getBeanProvider(PlatformTransactionManager.class));

    @BeforeEach void buyer() {
        when(accounts.currentAccountId()).thenReturn(Optional.of(7L));
        when(authentication.findByUserId(7L)).thenReturn(Optional.of(account(true, "USER")));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void queryUsesOnlyPrincipalBuyerAndScopedPersistedResult(boolean eligible) {
        when(orders.existsDeliveredItemForBuyer(23L, 7L)).thenReturn(eligible);
        assertThat(service.isCurrentBuyerEligibleToReview(23L)).isEqualTo(eligible);
        verify(orders).existsDeliveredItemForBuyer(23L, 7L);
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(items, identities, entities);
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1})
    void invalidItemIdsAreFalseWithoutOrderRead(Long item) {
        assertThat(service.isCurrentBuyerEligibleToReview(item)).isFalse();
        fails(ErrorCode.RESOURCE_NOT_FOUND, () -> service.withEligibleCurrentBuyerItem(item, facts -> "unused"));
        verifyNoInteractions(orders, items, identities, entities, manager);
    }

    @Test void missingPrincipalCannotReadOrWrite() {
        when(accounts.currentAccountId()).thenReturn(Optional.empty());
        fails(ErrorCode.UNAUTHENTICATED, () -> service.isCurrentBuyerEligibleToReview(23L));
        fails(ErrorCode.UNAUTHENTICATED, () -> service.withEligibleCurrentBuyerItem(23L, facts -> "unused"));
        verifyNoInteractions(orders, authentication, identities, manager);
    }

    @Test void missingPersistedAccountIsDenied() {
        when(authentication.findByUserId(7L)).thenReturn(Optional.empty());
        fails(ErrorCode.ACCESS_DENIED, () -> service.isCurrentBuyerEligibleToReview(23L));
        verifyNoInteractions(orders);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "MANAGER", "SHIPPER"})
    void persistedNonBuyerRoleIsDenied(String role) {
        when(authentication.findByUserId(7L)).thenReturn(Optional.of(account(true, role)));
        fails(ErrorCode.ACCESS_DENIED, () -> service.isCurrentBuyerEligibleToReview(23L));
        verifyNoInteractions(orders);
    }

    @Test void inactiveBuyerIsDenied() {
        when(authentication.findByUserId(7L)).thenReturn(Optional.of(account(false, "USER")));
        fails(ErrorCode.ACCESS_DENIED, () -> service.isCurrentBuyerEligibleToReview(23L));
        verifyNoInteractions(orders);
    }

    @Test void vendorCanBuyUnderExistingUserFacingRolePolicy() {
        when(authentication.findByUserId(7L)).thenReturn(Optional.of(account(true, "VENDOR")));
        when(orders.existsDeliveredItemForBuyer(23L, 7L)).thenReturn(true);
        assertThat(service.isCurrentBuyerEligibleToReview(23L)).isTrue();
    }

    @Test void nullCallbackFailsBeforeStartingTransaction() {
        fails(ErrorCode.INVALID_REQUEST, () -> service.withEligibleCurrentBuyerItem(23L, null));
        verifyNoInteractions(accounts, orders, manager);
    }

    @Test void persistenceOmittedContextsCannotAuthorizeAReadOrAWrite() {
        var empty = new StaticListableBeanFactory();
        var unavailable = new OrderEligibilityService(empty.getBeanProvider(OrderRepository.class),
                empty.getBeanProvider(OrderItemRepository.class), accounts, authentication, identities,
                empty.getBeanProvider(EntityManager.class), empty.getBeanProvider(PlatformTransactionManager.class));
        assertThatThrownBy(() -> unavailable.isCurrentBuyerEligibleToReview(23L))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
        assertThatThrownBy(() -> unavailable.withEligibleCurrentBuyerItem(23L,
                facts -> { throw new AssertionError("Missing persistence must never invoke writer"); }))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    private static AuthAccountSnapshot account(boolean active, String role) {
        return new AuthAccountSnapshot(7L, "buyer", null, active, 0L, Set.of(role));
    }
    private static void fails(ErrorCode code, Runnable work) {
        assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(code));
    }
}
