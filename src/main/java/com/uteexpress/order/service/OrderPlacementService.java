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
import com.uteexpress.payment.service.PaymentService;
import com.uteexpress.promotion.service.VoucherService;
import com.uteexpress.security.CurrentUserProvider;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shipping.dto.DeliveryAttemptOperation;
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

/** Single production lifecycle bean: placement, vendor mutations and internal shipper completion. */
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
    private final PaymentService payments;
    private final VoucherService vouchers;
    private final Clock clock;
    private final VendorOrderAuthority vendorAuthority;
    private final ShipperOrderAuthority shipperAuthority;
    private final OpsOrderAuthority opsAuthority;
    private final ReturnOrderAuthority returnAuthority;
    private final RefundOrderAuthority refundAuthority;
    private final com.uteexpress.payment.service.ManualCodRefundService refundPayments;
    private final com.uteexpress.shipping.service.ShipmentFulfillmentService shipments;
    private final com.uteexpress.shipping.service.ReturnToSenderService senderReturns;
    private final com.uteexpress.payment.service.PaymentReadService senderReturnPayments;

    public OrderPlacementService(OrderRepository orders, OrderItemRepository items,
            OrderStatusHistoryRepository history, CurrentUserProvider users,
            PlatformTransactionManager transactionManager, ApplicationEventPublisher events, Clock clock,
            CurrentAccountIdProvider accounts, AccountIdentityService identities, CartService carts,
            CheckoutQuoteService quotes, InventoryService inventory, CommissionQueryService commissions,
            PaymentService payments, VendorOrderAuthority vendorAuthority, VoucherService vouchers,
            jakarta.persistence.EntityManager entityManager,
            com.uteexpress.shipping.service.ShipmentFulfillmentService shipments,
            com.uteexpress.payment.service.PaymentReadService paymentReads,
            com.uteexpress.governance.service.RoleGovernanceService roles,
            com.uteexpress.governance.service.AuditLogService audit, ReturnOrderAuthority returnAuthority,
            com.uteexpress.shipping.service.ShipmentAttemptService attempts,
            RefundOrderAuthority refundAuthority, com.uteexpress.payment.service.ManualCodRefundService refundPayments,
            com.uteexpress.shipping.service.ReturnToSenderService senderReturns) {
        super(orders, items, history, users, transactionManager, events, clock);
        this.orders = orders;
        this.accounts = accounts;
        this.identities = identities;
        this.carts = carts;
        this.quotes = quotes;
        this.inventory = inventory;
        this.commissions = commissions;
        this.payments = payments;
        this.vouchers = vouchers;
        this.clock = clock;
        this.vendorAuthority = vendorAuthority;
        this.shipments = shipments;
        this.senderReturns = senderReturns;
        this.senderReturnPayments = paymentReads;
        this.returnAuthority = returnAuthority;
        this.refundAuthority = refundAuthority;
        this.refundPayments = refundPayments;
        this.shipperAuthority = new ShipperOrderAuthority(orders, entityManager, shipments, paymentReads, attempts);
        this.opsAuthority = new OpsOrderAuthority(orders, items, entityManager, accounts, identities,
                roles, inventory, vouchers, audit);
    }

    @Override protected ReturnOrderAuthority returnAuthority() { return returnAuthority; }
    @Override protected com.uteexpress.shipping.service.ReturnToSenderService returnToSenderShipments() { return senderReturns; }
    @Override protected com.uteexpress.payment.service.PaymentReadService returnToSenderPayments() { return senderReturnPayments; }
    @Override protected void releaseReturnedInventory(Order order, java.time.Instant at) { opsAuthority.releaseInventory(order,at); }
    @Override protected void releaseCancelledVoucher(Order order) { opsAuthority.releaseCancelledVoucher(order); }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    public com.uteexpress.order.dto.ReturnToSenderResult confirmReturnToSender(com.uteexpress.order.dto.ReturnToSenderCommand command) {
        return super.confirmReturnToSender(command);
    }
    @Override protected RefundOrderAuthority refundAuthority() { return refundAuthority; }
    @Override protected com.uteexpress.payment.service.ManualCodRefundService refundPayments() { return refundPayments; }

    @Override
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority())")
    public com.uteexpress.payment.dto.RefundResult completeRefund(com.uteexpress.payment.dto.ManualRefundCommand command) {
        return super.completeRefund(command);
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
            + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public com.uteexpress.order.dto.OrderStatusChangedEvent requestReturn(
            com.uteexpress.order.dto.RequestReturnCommand command) {
        return super.requestReturn(command);
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public void approveReturn(com.uteexpress.order.dto.ReturnDecisionCommand command) {
        super.approveReturn(command);
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public com.uteexpress.order.dto.OrderStatusChangedEvent rejectReturn(
            com.uteexpress.order.dto.ReturnDecisionCommand command) {
        return super.rejectReturn(command);
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), "
            + "T(com.uteexpress.security.RoleCode).MANAGER.authority())")
    public com.uteexpress.order.dto.OrderStatusChangedEvent cancelForOps(
            com.uteexpress.order.dto.OpsOrderCancellationCommand command) {
        return super.cancelForOps(command);
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public com.uteexpress.order.dto.OrderStatusChangedEvent receiveReturn(
            com.uteexpress.order.dto.ReceiveReturnCommand command) {
        return super.receiveReturn(command);
    }

    @Override protected Order loadForOpsMutation(Long id) { return opsAuthority.lockOrder(id); }

    @Override protected java.util.Optional<com.uteexpress.shipping.dto.ShipmentFulfillmentFacts> lockShipmentForOps(
            Order order, com.uteexpress.order.dto.OpsOrderCancellationCommand command) {
        return shipments.lockForOpsCancellation(order.getId());
    }

    @Override protected Long authorizeOps(Order order) { return opsAuthority.authorize(); }

    @Override protected void resolveShipmentForOps(Order order,
            java.util.Optional<com.uteexpress.shipping.dto.ShipmentFulfillmentFacts> shipment, java.time.Instant at) {
        shipments.cancelLockedForOpsOrder(order.getId(), shipment.map(
                com.uteexpress.shipping.dto.ShipmentFulfillmentFacts::version).orElse(null));
    }

    @Override protected void beforeOpsCancellation(Order order, java.time.Instant at) {
        opsAuthority.release(order, at);
    }

    @Override protected void auditOpsCancellation(Order order, com.uteexpress.order.dto.OrderStatus from,
            Long originalVersion, Long actor, com.uteexpress.order.dto.OpsCancellationReason reason) {
        opsAuthority.appendAudit(order, from, originalVersion, actor, reason);
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void prepareShipperTransition(Long orderId, Long expectedOrderVersion,
            Long expectedShipmentVersion, com.uteexpress.order.dto.OrderAction action) {
        try {
            shipperAuthority.requireTransition(orderId, expectedOrderVersion, expectedShipmentVersion, action, false);
        } catch (org.springframework.dao.OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public com.uteexpress.order.dto.OrderStatusChangedEvent completeShipperTransition(Long orderId,
            Long expectedOrderVersion, Long expectedShipmentVersion, com.uteexpress.order.dto.OrderAction action) {
        try {
            var guarded = shipperAuthority.requireTransition(orderId, expectedOrderVersion, expectedShipmentVersion, action, true);
            return persistValidatedTransition(guarded.order(),
                    OrderTransitionPolicy.requireTarget(guarded.order().getStatus(), action), guarded.actorId(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS), null);
        } catch (org.springframework.dao.OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    @Override
    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
            + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public com.uteexpress.order.dto.OrderStatusChangedEvent transition(
            com.uteexpress.order.dto.OrderTransitionCommand command) {
        return super.transition(command);
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void prepareShipmentOnlyOperation(Long orderId, Long expectedOrderVersion,
            Long expectedShipmentVersion, DeliveryAttemptOperation operation) {
        requireShipmentOnlyOperation(orderId, expectedOrderVersion, expectedShipmentVersion, operation, false);
    }

    @Override
    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).SHIPPER.authority())")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void verifyShipmentOnlyOperation(Long orderId, Long expectedOrderVersion,
            Long expectedShipmentVersion, DeliveryAttemptOperation operation) {
        requireShipmentOnlyOperation(orderId, expectedOrderVersion, expectedShipmentVersion, operation, true);
    }

    private void requireShipmentOnlyOperation(Long orderId, Long expectedOrderVersion,
            Long expectedShipmentVersion, DeliveryAttemptOperation operation, boolean completion) {
        Long actor = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        try {
            shipperAuthority.requireShipmentOnlyOperation(orderId, expectedOrderVersion,
                    expectedShipmentVersion, operation, completion, actor);
        } catch (org.springframework.dao.OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    @Override protected Order loadForMutation(Long id) { return vendorAuthority.lockOwnedOrder(id); }

    @Override protected Authorization authorize(com.uteexpress.security.CurrentUser user, Order order,
            com.uteexpress.order.dto.OrderTransitionCommand command) {
        return vendorAuthority.authorize(order, command);
    }

    @Override protected void beforeValidatedTransition(Order order,
            com.uteexpress.order.dto.OrderTransitionCommand command, java.time.Instant at) {
        vendorAuthority.beforeTransition(order, command, at);
        // Vendor effects have acquired products then Shop. Only these pre-delivery cancellation
        // intents release quota; confirmation/ready and future return/refund transitions do not.
        if (command.action() == com.uteexpress.order.dto.OrderAction.CANCEL_NEW
                || command.action() == com.uteexpress.order.dto.OrderAction.CANCEL_CONFIRMED) {
            vouchers.releaseForCancellation(order.getId());
        }
    }

    @Override protected void authorizeReady(com.uteexpress.security.CurrentUser user, Order order) {
        vendorAuthority.authorizeReady(order);
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
        // ONLINE remains outside this placement contract.
        if (request.paymentMethod() != CheckoutRequest.PaymentMethod.COD) {
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
        // After inventory/availability locks, revalidate persisted voucher rules and both quotas under its row lock.
        var voucher = vouchers.lockForCheckout(request.voucherCode(), fresh.shopId(), fresh.totals().subtotal());
        var totals = OrderTotals.calculate(fresh.totals().subtotal(),
                voucher == null ? BigDecimal.ZERO : voucher.discountAmount(), fresh.totals().shippingFee());
        var checkoutAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var policy = commissions.requireEffectivePolicy(checkoutAt);
        if (policy.policyId() == null || policy.policyId() <= 0 || policy.ratePercent() == null
                || policy.ratePercent().signum() < 0 || policy.ratePercent().compareTo(new BigDecimal("100")) > 0) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        var commission = Money.round(totals.subtotal().subtract(totals.discountTotal())
                .multiply(policy.ratePercent()).movePointLeft(2));
        var snapshot = new CheckoutQuote(fresh.shopId(), fresh.items(), fresh.address(), totals,
                fresh.shippingProviderId(), fresh.shippingServiceSnapshot(), policy.policyId(),
                policy.ratePercent(), commission, voucher);
        inventory.decrease(selected.stream().map(line -> new StockQuantity(line.productId(), line.quantity())).toList());
        Order order = persistNew(buyer, "ORD-" + UUID.randomUUID(), key, hash, snapshot, checkoutAt);
        if (voucher != null) vouchers.recordUsage(voucher, order.getId());
        payments.initializeCodForNewOrder(order.getId());
        carts.removeCheckedOutItems(selected);
        return result(order, false);
    }

    private static PlaceOrderResult result(Order order, boolean replayed) {
        return new PlaceOrderResult(order.getId(), order.getOrderCode(), order.getStatus(),
                order.getGrandTotal(), order.getCreatedAt(), replayed);
    }
}
