package com.uteexpress.order.service;

import com.uteexpress.catalog.dto.StockQuantity;
import com.uteexpress.catalog.service.InventoryService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.order.dto.OrderTransitionCommand;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.service.VendorShopQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Trusted vendor guards/effects for the shared lifecycle, never a second state machine. */
@Service
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
@Transactional(propagation = Propagation.MANDATORY)
public class VendorOrderAuthority {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final VendorShopQueryService shops;
    private final InventoryService inventory;
    private final PaymentReadService payments;
    private final com.uteexpress.shipping.service.ShipmentFulfillmentService shipments;
    private final EntityManager entityManager;

    public VendorOrderAuthority(OrderRepository orders, OrderItemRepository items,
            CurrentAccountIdProvider accounts, AccountIdentityService identities, VendorShopQueryService shops,
            InventoryService inventory, PaymentReadService payments,
            EntityManager entityManager, com.uteexpress.shipping.service.ShipmentFulfillmentService shipments) {
        this.orders = orders;
        this.items = items;
        this.accounts = accounts;
        this.identities = identities;
        this.shops = shops;
        this.inventory = inventory;
        this.payments = payments;
        this.entityManager = entityManager;
        this.shipments = shipments;
    }

    public Order lockOwnedOrder(Long id) {
        Long vendor = vendorId();
        identities.requireActiveAccountForUpdate(vendor);
        Long shop = shops.requireApprovedOwnedShop(vendor).shopId();
        var order = orders.findByIdAndShopIdForUpdate(id, shop)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        // Discard an earlier managed snapshot if a caller joined an existing transaction.
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    public OrderLifecycleServiceImpl.Authorization authorize(Order order, OrderTransitionCommand command) {
        Long vendor = vendorId();
        if (!shops.requireApprovedOwnedShop(vendor).shopId().equals(order.getShopId())) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (command.action() != OrderAction.CONFIRM && command.action() != OrderAction.CANCEL_NEW
                && command.action() != OrderAction.CANCEL_CONFIRMED) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        boolean cancellation = command.action() != OrderAction.CONFIRM;
        if (cancellation && (command.reason() == null
                || !java.util.Set.of("OUT_OF_STOCK", "UNABLE_TO_FULFILL").contains(command.reason()))) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        // Keep unchecked browser free text out of persisted history/events, per ORD-01.
        return new OrderLifecycleServiceImpl.Authorization(vendor,
                cancellation ? (command.reason().equals("OUT_OF_STOCK")
                        ? "Vendor cancellation: out of stock" : "Vendor cancellation: unable to fulfill") : null);
    }

    public void beforeTransition(Order order, OrderTransitionCommand command, Instant at) {
        if (command.action() == OrderAction.CONFIRM) {
            payments.requireConfirmablePayment(order.getId(), order.getGrandTotal());
        } else {
            if (order.getInventoryReleasedAt() != null) throw new ApplicationException(ErrorCode.CONFLICT);
            shipments.cancelAssignedForVendorOrder(order.getId());
            var quantities = items.findByOrderIdOrderByIdAsc(order.getId()).stream()
                    .map(item -> new StockQuantity(item.getProductId(), item.getQuantity())).toList();
            inventory.restore(quantities);
            order.releaseInventory(at);
        }
        // Checkout locks products before shop: cancellation takes this shop lock only AFTER restore.
        shops.requireApprovedOwnedShopForUpdate(vendorId());
    }

    public void authorizeReady(Order order) {
        if (!shops.requireApprovedOwnedShop(vendorId()).shopId().equals(order.getShopId())) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        shops.requireApprovedOwnedShopForUpdate(vendorId());
    }

    private Long vendorId() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }
}
