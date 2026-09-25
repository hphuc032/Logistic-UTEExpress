package com.uteexpress.cart;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.entity.Cart;
import com.uteexpress.cart.entity.CartItem;
import com.uteexpress.cart.repository.CartRepository;
import com.uteexpress.cart.repository.CartItemRepository;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.catalog.dto.ProductSnapshot;
import com.uteexpress.catalog.service.CatalogQueryService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartServiceTest {
    CartRepository carts = mock(CartRepository.class);
    CartItemRepository items = mock(CartItemRepository.class);
    CurrentAccountIdProvider account = mock(CurrentAccountIdProvider.class);
    CatalogQueryService catalog = mock(CatalogQueryService.class);
    Cart cart = mock(Cart.class);
    Instant now = Instant.parse("2026-09-25T00:00:00Z");
    CartService service = new CartService(carts, items, account, catalog, Clock.fixed(now, ZoneOffset.UTC));

    @BeforeEach
    void owner() {
        when(account.currentAccountId()).thenReturn(Optional.of(10L));
        when(cart.getId()).thenReturn(20L);
        when(carts.lockByUserId(10L)).thenReturn(Optional.of(cart));
        when(catalog.requirePurchasableProducts(Set.of(30L))).thenReturn(List.of(
                new ProductSnapshot(30L, 40L, "Current product", new BigDecimal("125000"), 0L)));
    }

    @Test
    void authenticatedOwnerCanCreateAndReadCart() {
        assertThat(service.getOrCreateCart().id()).isEqualTo(20L);
        verify(carts).createIfAbsent(10L);
        when(carts.findByUserId(10L)).thenReturn(Optional.of(cart));
        assertThat(service.getCurrentUserCart()).isPresent();
        verify(items, times(2)).findAllByCartIdAndCartUserIdOrderById(20L, 10L);
    }

    @Test
    void absentAuthenticationRejectsEveryOperationBeforePersistence() {
        when(account.currentAccountId()).thenReturn(Optional.empty());
        assertError(service::getOrCreateCart, ErrorCode.UNAUTHENTICATED);
        assertError(service::getCurrentUserCart, ErrorCode.UNAUTHENTICATED);
        assertError(() -> service.addProduct(new AddCartProductRequest(30L, 1)), ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(carts, items, catalog);
    }

    @Test
    void firstAddCreatesItemWithRequestedQuantityAndServerTime() {
        service.addProduct(new AddCartProductRequest(30L, 2));
        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(items).save(saved.capture());
        assertThat(saved.getValue().getProductId()).isEqualTo(30L);
        assertThat(saved.getValue().getQuantity()).isEqualTo(2);
        verify(cart).touch(now);
    }

    @Test
    void repeatedAddChangesExistingItemWithoutInserting() {
        CartItem item = CartItem.create(cart, 30L, 2, now);
        when(items.findByCartIdAndProductIdAndCartUserId(20L, 30L, 10L)).thenReturn(Optional.of(item));
        service.addProduct(new AddCartProductRequest(30L, 3));
        assertThat(item.getQuantity()).isEqualTo(5);
        verify(items, never()).save(any());
    }

    @Test
    void invalidRequestsAreRejectedBeforeCatalogOrPersistence() {
        for (AddCartProductRequest request : new AddCartProductRequest[]{null,
                new AddCartProductRequest(null, 1), new AddCartProductRequest(-1L, 1),
                new AddCartProductRequest(30L, null), new AddCartProductRequest(30L, 0),
                new AddCartProductRequest(30L, -1)}) {
            assertError(() -> service.addProduct(request), ErrorCode.VALIDATION_FAILED);
        }
        verifyNoInteractions(carts, items, catalog);
    }

    @Test
    void missingProductUsesCatalogFailureAndDoesNotCreateCart() {
        when(catalog.requirePurchasableProducts(Set.of(30L)))
                .thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        assertError(() -> service.addProduct(new AddCartProductRequest(30L, 1)), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(carts, items);
    }

    @Test
    void subtotalUsesCurrentCatalogPriceAndSingleBatchLookup() {
        when(carts.findByUserId(10L)).thenReturn(Optional.of(cart));
        when(items.findAllByCartIdAndCartUserIdOrderById(20L, 10L))
                .thenReturn(List.of(CartItem.create(cart, 30L, 3, now)));
        assertThat(service.getCurrentUserCart().orElseThrow().subtotal()).isEqualByComparingTo("375000");
        verify(catalog).requirePurchasableProducts(Set.of(30L));
    }

    @Test
    void overflowIsRejectedWithoutWrappingQuantity() {
        CartItem item = CartItem.create(cart, 30L, Integer.MAX_VALUE, now);
        when(items.findByCartIdAndProductIdAndCartUserId(20L, 30L, 10L)).thenReturn(Optional.of(item));
        assertError(() -> service.addProduct(new AddCartProductRequest(30L, 1)), ErrorCode.VALIDATION_FAILED);
        assertThat(item.getQuantity()).isEqualTo(Integer.MAX_VALUE);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApplicationException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
