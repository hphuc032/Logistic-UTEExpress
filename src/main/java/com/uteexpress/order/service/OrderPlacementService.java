package com.uteexpress.order.service;

import com.uteexpress.cart.service.CartService;
import com.uteexpress.catalog.dto.StockQuantity;
import com.uteexpress.catalog.service.InventoryService;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.service.CommissionQueryService;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.order.dto.PlaceOrderResult;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.*;
import com.uteexpress.security.CurrentUserProvider;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

/** CHK-02 creation under lifecycle authority. Inherited later workflow guards still deny. */
@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
        + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
public class OrderPlacementService extends OrderLifecycleServiceImpl {
    private final OrderRepository orders;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final CartService carts;
    private final CheckoutQuoteService quotes;
    private final InventoryService inventory;
    private final CommissionQueryService commissions;
    private final Clock clock;

    public OrderPlacementService(OrderRepository orders, OrderItemRepository items,
            OrderStatusHistoryRepository history, CurrentUserProvider users,
            PlatformTransactionManager transactionManager, ApplicationEventPublisher events, Clock clock,
            CurrentAccountIdProvider accounts, AccountIdentityService identities, CartService carts,
            CheckoutQuoteService quotes, InventoryService inventory, CommissionQueryService commissions) {
        super(orders, items, history, users, transactionManager, events, clock);
        this.orders = orders;
        this.accounts = accounts;
        this.identities = identities;
        this.carts = carts;
        this.quotes = quotes;
        this.inventory = inventory;
        this.commissions = commissions;
        this.clock = clock;
    }

    /** One REQUIRED transaction includes locks, fresh quote, stock, order/items/history and cleanup. */
    @Transactional
    public PlaceOrderResult placeOrder(CheckoutRequest request) {
        Long buyer = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        String hash = CheckoutRequestHash.calculate(request);
        String key = request.checkoutKey().strip();
        // Stable buyer row serializes duplicate submissions even after the selected cart lines disappear.
        // The database buyer/key uniqueness constraint remains the final persistence guard.
        identities.requireActiveAccountForUpdate(buyer);
        var existing = orders.findByBuyerIdAndCheckoutKey(buyer, key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) throw new ApplicationException(ErrorCode.CONFLICT);
            return result(existing.get(), true);
        }
        // PAY-01 and voucher engines are outside CHK-02; do not silently ignore unsupported selections.
        if (request.paymentMethod() != CheckoutRequest.PaymentMethod.COD
                || (request.voucherCode() != null && !request.voucherCode().isBlank())) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        var selected = carts.lockSelectedItemsForCheckout();
        if (selected.isEmpty()) throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        var requested = request.items().stream().collect(Collectors.toMap(CheckoutRequest.Item::productId,
                CheckoutRequest.Item::quantity));
        if (selected.size() != requested.size() || selected.stream().anyMatch(line ->
                !Integer.valueOf(line.quantity()).equals(requested.get(line.productId())))) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        // Reuse CHK-01 validation inside this transaction; its inventory locks are retained until commit.
        var fresh = quotes.quote(new QuoteRequest(request.addressId(), request.shippingProviderId(),
                request.shippingServiceCode())).quote();
        var checkoutAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var policy = commissions.requireEffectivePolicy(checkoutAt);
        if (policy.policyId() == null || policy.policyId() <= 0 || policy.ratePercent() == null
                || policy.ratePercent().signum() < 0 || policy.ratePercent().compareTo(new BigDecimal("100")) > 0) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        var commission = Money.round(fresh.totals().subtotal().subtract(fresh.totals().discountTotal())
                .multiply(policy.ratePercent()).movePointLeft(2));
        var snapshot = new CheckoutQuote(fresh.shopId(), fresh.items(), fresh.address(), fresh.totals(),
                fresh.shippingProviderId(), fresh.shippingServiceSnapshot(), policy.policyId(),
                policy.ratePercent(), commission);
        inventory.decrease(selected.stream().map(line -> new StockQuantity(line.productId(), line.quantity())).toList());
        Order order = persistNew(buyer, "ORD-" + UUID.randomUUID(), key, hash, snapshot, checkoutAt);
        carts.removeCheckedOutItems(selected);
        return result(order, false);
    }

    private static PlaceOrderResult result(Order order, boolean replayed) {
        return new PlaceOrderResult(order.getId(), order.getOrderCode(), order.getStatus(),
                order.getGrandTotal(), order.getCreatedAt(), replayed);
    }
}
