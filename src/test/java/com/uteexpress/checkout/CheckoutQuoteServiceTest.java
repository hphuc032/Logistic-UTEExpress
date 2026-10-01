package com.uteexpress.checkout;

import com.uteexpress.account.dto.AddressData;
import com.uteexpress.account.service.AddressQueryService;
import com.uteexpress.cart.dto.*;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.catalog.dto.*;
import com.uteexpress.catalog.service.*;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import com.uteexpress.common.exception.*;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shipping.dto.*;
import com.uteexpress.shipping.service.ShippingQuoteService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutQuoteServiceTest {
    final CurrentAccountIdProvider accounts = mock(CurrentAccountIdProvider.class);
    final AddressQueryService addresses = mock(AddressQueryService.class);
    final CartService carts = mock(CartService.class);
    final CatalogQueryService catalog = mock(CatalogQueryService.class);
    final InventoryService inventory = mock(InventoryService.class);
    final ShippingQuoteService shipping = mock(ShippingQuoteService.class);
    final CheckoutQuoteService checkout = new CheckoutQuoteService(accounts, addresses, carts, catalog, inventory, shipping);
    final QuoteRequest request = new QuoteRequest(2L, 3L, "STANDARD");

    @BeforeEach void fixture() {
        when(accounts.currentAccountId()).thenReturn(Optional.of(1L));
        when(addresses.requireOwnedAddress(1L, 2L)).thenReturn(new AddressData(2L, "Receiver", "0900000000", "VN",
                "District", "DB detail", true));
        cart(line(10L, 2, true, CartItemStatus.AVAILABLE));
        when(catalog.requirePurchasableProducts(anySet())).thenReturn(List.of(product(10L, 5L, "125")));
        when(shipping.quote(any())).thenReturn(new ShippingQuote(3L, "STANDARD", new BigDecimal("17"), 0L));
    }

    @Test void currentCatalogPriceAndAuthoritativeAddressAndShippingAreUsed() {
        var preview = checkout.quote(request);
        assertThat(preview.quote().totals().subtotal()).isEqualByComparingTo("250");
        assertThat(preview.quote().totals().shippingFee()).isEqualByComparingTo("17");
        assertThat(preview.quote().totals().grandTotal()).isEqualByComparingTo("267");
        assertThat(preview.quote().items().getFirst().unitPrice()).isEqualByComparingTo("125");
        verify(shipping).quote(new ShippingQuoteCommand(5L, 3L, "STANDARD", "VN", "District", "DB detail"));
        verify(inventory).lockAndCheck(List.of(new StockQuantity(10L, 2)));
        verifyNoMoreInteractions(inventory);
        verify(carts).getCurrentUserCart();
        verifyNoMoreInteractions(carts);
    }

    @Test void multipleSelectedShopsAreRejected() {
        cart(line(10L, 2, true, CartItemStatus.AVAILABLE), line(11L, 3, true, CartItemStatus.AVAILABLE),
                line(12L, 1, true, CartItemStatus.AVAILABLE), line(13L, 4, false, CartItemStatus.UNAVAILABLE));
        when(catalog.requirePurchasableProducts(Set.of(10L, 11L, 12L))).thenReturn(List.of(
                product(10L, 5L, "100"), product(11L, 5L, "20"), product(12L, 6L, "40")));
        rejects(ErrorCode.CONFLICT);
        verifyNoInteractions(shipping);
        verify(inventory).lockAndCheck(List.of(new StockQuantity(10L, 2), new StockQuantity(11L, 3), new StockQuantity(12L, 1)));
        verifyNoMoreInteractions(inventory);
        verify(carts).getCurrentUserCart();
        verifyNoMoreInteractions(carts);
    }

    @Test void sameShopMultipleProductsUseOneShippingQuote() {
        cart(line(10L, 2, true, CartItemStatus.AVAILABLE), line(11L, 3, true, CartItemStatus.AVAILABLE));
        when(catalog.requirePurchasableProducts(Set.of(10L, 11L))).thenReturn(List.of(
                product(10L, 5L, "100"), product(11L, 5L, "20")));
        var quote = checkout.quote(request).quote();
        assertThat(quote.shopId()).isEqualTo(5L);
        assertThat(quote.items()).hasSize(2);
        assertThat(quote.totals().subtotal()).isEqualByComparingTo("260");
        assertThat(quote.totals().shippingFee()).isEqualByComparingTo("17");
        assertThat(quote.totals().grandTotal()).isEqualByComparingTo("277");
        verify(shipping).quote(new ShippingQuoteCommand(5L, 3L, "STANDARD", "VN", "District", "DB detail"));
        verifyNoMoreInteractions(shipping);
    }

    @Test void missingCartRejected() {
        when(carts.getCurrentUserCart()).thenReturn(Optional.empty());
        rejects(ErrorCode.INVALID_REQUEST);
        verifyNoInteractions(inventory, shipping);
    }
    @Test void emptyCartRejected() { cart(); rejects(ErrorCode.INVALID_REQUEST); }
    @Test void noSelectionRejected() {
        cart(line(10L, 2, false, CartItemStatus.AVAILABLE)); rejects(ErrorCode.INVALID_REQUEST);
    }
    @Test void mixedUnavailableSelectionFailsEntireQuote() {
        cart(line(10L, 2, true, CartItemStatus.AVAILABLE), line(11L, 1, true, CartItemStatus.UNAVAILABLE));
        rejects(ErrorCode.CONFLICT); verifyNoInteractions(inventory, shipping);
    }
    @ParameterizedTest @ValueSource(ints = {0, -1})
    void invalidQuantityRejected(int quantity) {
        cart(line(10L, quantity, true, CartItemStatus.AVAILABLE)); rejects(ErrorCode.VALIDATION_FAILED);
    }
    @Test void duplicateProductsRejected() {
        cart(line(10L, 2, true, CartItemStatus.AVAILABLE), line(10L, 1, true, CartItemStatus.AVAILABLE));
        rejects(ErrorCode.VALIDATION_FAILED);
    }
    @Test void insufficientStockDuringLockCheckFails() {
        doThrow(new ApplicationException(ErrorCode.CONFLICT)).when(inventory).lockAndCheck(anyList());
        rejects(ErrorCode.CONFLICT); verifyNoInteractions(shipping);
    }
    @Test void missingProductDoesNotProducePartialQuote() {
        when(catalog.requirePurchasableProducts(anySet())).thenReturn(List.of());
        rejects(ErrorCode.RESOURCE_NOT_FOUND);
    }
    @Test void unavailableCatalogBatchFails() {
        when(catalog.requirePurchasableProducts(anySet())).thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        rejects(ErrorCode.RESOURCE_NOT_FOUND);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "0.5", "99999999999999999"})
    void invalidOrOverflowingMoneyFails(String price) {
        when(catalog.requirePurchasableProducts(anySet())).thenReturn(List.of(product(10L, 5L, price)));
        rejects(ErrorCode.VALIDATION_FAILED);
    }
    @Test void noShippingFallback() {
        when(shipping.quote(any())).thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        rejects(ErrorCode.RESOURCE_NOT_FOUND);
    }
    @Test void foreignAddressFailsBeforeCartAccess() {
        when(addresses.requireOwnedAddress(1L, 2L)).thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        rejects(ErrorCode.RESOURCE_NOT_FOUND); verifyNoInteractions(carts, inventory, shipping);
    }
    @Test void unauthenticatedFailsBeforeDataAccess() {
        when(accounts.currentAccountId()).thenReturn(Optional.empty());
        rejects(ErrorCode.UNAUTHENTICATED); verifyNoInteractions(addresses, carts, inventory, shipping);
    }
    @Test void nullAndMalformedRequestRejected() {
        for (var invalid : Arrays.asList(null, new QuoteRequest(null, 3L, "STANDARD"),
                new QuoteRequest(2L, 0L, "STANDARD"), new QuoteRequest(2L, 3L, "bad"))) {
            assertThatThrownBy(() -> checkout.quote(invalid)).isInstanceOf(ApplicationException.class);
        }
    }
    @Test void optionsUseOwnedAddress() {
        checkout.shippingOptions(2L);
        verify(addresses).requireOwnedAddress(1L, 2L);
        verify(shipping).availableServices("VN");
    }
    private void rejects(ErrorCode code) {
        assertThatThrownBy(() -> checkout.quote(request)).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(code));
    }
    private void cart(CartItemView... items) {
        when(carts.getCurrentUserCart()).thenReturn(Optional.of(new CartView(4L, List.of(items), BigDecimal.ZERO)));
    }
    private CartItemView line(Long id, int quantity, boolean selected, CartItemStatus status) {
        return new CartItemView(id, id, "Stale name", quantity, selected, BigDecimal.ONE, BigDecimal.ONE, 10, status);
    }
    private ProductSnapshot product(Long id, Long shop, String price) {
        return new ProductSnapshot(id, shop, "Current product", new BigDecimal(price), 0L);
    }
}
