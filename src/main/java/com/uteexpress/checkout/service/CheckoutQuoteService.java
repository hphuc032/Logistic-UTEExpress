package com.uteexpress.checkout.service;

import com.uteexpress.account.dto.AddressData;
import com.uteexpress.account.service.AddressQueryService;
import com.uteexpress.cart.dto.CartItemView;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.catalog.dto.StockQuantity;
import com.uteexpress.catalog.service.CatalogQueryService;
import com.uteexpress.catalog.service.InventoryService;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.promotion.service.VoucherService;
import com.uteexpress.shipping.dto.ShippingQuoteCommand;
import com.uteexpress.shipping.dto.ShippingRateView;
import com.uteexpress.shipping.service.ShippingQuoteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
        + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class CheckoutQuoteService {
    private final CurrentAccountIdProvider accounts;
    private final AddressQueryService addresses;
    private final CartService carts;
    private final CatalogQueryService catalog;
    private final InventoryService inventory;
    private final ShippingQuoteService shipping;
    private final VoucherService vouchers;

    public CheckoutQuoteService(CurrentAccountIdProvider accounts, AddressQueryService addresses,
            CartService carts, CatalogQueryService catalog, InventoryService inventory, ShippingQuoteService shipping,
            VoucherService vouchers) {
        this.accounts = accounts;
        this.addresses = addresses;
        this.carts = carts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.shipping = shipping;
        this.vouchers = vouchers;
    }

    @Transactional(readOnly = true)
    public List<AddressData> addresses() { return addresses.listOwnedAddresses(owner()); }

    @Transactional(readOnly = true)
    public List<ShippingRateView> shippingOptions(Long addressId) {
        AddressData address = ownedAddress(addressId);
        return shipping.availableServices(address.provinceCode());
    }

    /**
     * Short transaction only: InventoryService locks are released before returning to the controller.
     * No reserve/decrease or other write occurs. CHK-02 must revalidate all prices, stock and shipping.
     */
    @Transactional
    public CheckoutPreview quote(QuoteRequest request) {
        owner();
        if (request == null || request.shippingProviderId() == null || request.shippingProviderId() <= 0
                || request.shippingServiceCode() == null
                || !request.shippingServiceCode().matches("[A-Z][A-Z0-9_]{0,31}")) fail(ErrorCode.VALIDATION_FAILED);
        AddressData address = ownedAddress(request.addressId());
        var cart = carts.getCurrentUserCart().orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_REQUEST));
        List<CartItemView> selected = cart.items().stream().filter(CartItemView::selected).toList();
        if (selected.isEmpty()) fail(ErrorCode.INVALID_REQUEST);
        for (var item : selected) {
            if (item.quantity() <= 0) fail(ErrorCode.VALIDATION_FAILED);
            if (!item.available()) fail(ErrorCode.CONFLICT);
        }
        var ids = selected.stream().map(CartItemView::productId).collect(Collectors.toSet());
        if (ids.size() != selected.size()) fail(ErrorCode.VALIDATION_FAILED);
        inventory.lockAndCheck(selected.stream().map(item -> new StockQuantity(item.productId(), item.quantity())).toList());
        var products = catalog.requirePurchasableProductsForCheckout(ids).stream()
                .collect(Collectors.toMap(com.uteexpress.catalog.dto.ProductSnapshot::productId, Function.identity()));
        if (!products.keySet().equals(ids)) fail(ErrorCode.RESOURCE_NOT_FOUND);
        Set<Long> shopIds = new HashSet<>();
        List<CheckoutQuote.ItemSnapshot> items = new ArrayList<>();
        for (var item : selected) {
            var product = products.get(item.productId());
            if (product.shopId() == null || product.shopId() <= 0) fail(ErrorCode.RESOURCE_NOT_FOUND);
            BigDecimal price = Money.requireAmount(product.unitPrice());
            if (price.signum() <= 0) fail(ErrorCode.VALIDATION_FAILED);
            BigDecimal lineTotal = Money.requireAmount(price.multiply(BigDecimal.valueOf(item.quantity())));
            shopIds.add(product.shopId());
            items.add(new CheckoutQuote.ItemSnapshot(
                    product.productId(), product.productName(), price, Money.round(BigDecimal.ZERO), price, item.quantity(), lineTotal));
        }
        if (shopIds.size() != 1) throw new ApplicationException(ErrorCode.Detail.SINGLE_SHOP_CHECKOUT);
        Long shopId = shopIds.iterator().next();
        var snapshot = new CheckoutQuote.AddressSnapshot(address.receiverName(), address.phone(), address.provinceCode(),
                address.district(), address.detail());
        var fee = shipping.quote(new ShippingQuoteCommand(shopId, request.shippingProviderId(),
                request.shippingServiceCode(), address.provinceCode(), address.district(), address.detail()));
        BigDecimal subtotal = items.stream().map(CheckoutQuote.ItemSnapshot::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var voucher = vouchers.preview(request.voucherCode(), shopId, subtotal);
        var totals = OrderTotals.calculate(subtotal, voucher == null ? BigDecimal.ZERO : voucher.discountAmount(), fee.shippingFee());
        // One checkout = one shop = one prospective order. Commission remains unresolved.
        return new CheckoutPreview(new CheckoutQuote(shopId, items, snapshot, totals,
                fee.providerId(), fee.serviceCode(), null, null, null, voucher));
    }

    private AddressData ownedAddress(Long addressId) {
        Long owner = owner();
        if (addressId == null || addressId <= 0) fail(ErrorCode.VALIDATION_FAILED);
        return addresses.requireOwnedAddress(owner, addressId);
    }

    private Long owner() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static void fail(ErrorCode code) { throw new ApplicationException(code); }
}
