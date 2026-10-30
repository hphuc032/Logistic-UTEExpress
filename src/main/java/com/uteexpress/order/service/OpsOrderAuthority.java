package com.uteexpress.order.service;

import com.uteexpress.catalog.dto.StockQuantity;
import com.uteexpress.catalog.service.InventoryService;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.governance.service.RoleGovernanceService;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.promotion.service.VoucherService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shipping.dto.ShipmentFulfillmentFacts;
import com.uteexpress.shipping.dto.ShipmentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Order-internal components. Only the lifecycle orchestrator may compose these effects. */
final class OpsOrderAuthority {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final EntityManager entityManager;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final RoleGovernanceService roles;
    private final InventoryService inventory;
    private final VoucherService vouchers;
    private final AuditLogService audit;

    OpsOrderAuthority(OrderRepository orders, OrderItemRepository items, EntityManager entityManager,
            CurrentAccountIdProvider accounts, AccountIdentityService identities, RoleGovernanceService roles,
            InventoryService inventory, VoucherService vouchers, AuditLogService audit) {
        this.orders = orders; this.items = items; this.entityManager = entityManager;
        this.accounts = accounts; this.identities = identities; this.roles = roles;
        this.inventory = inventory; this.vouchers = vouchers; this.audit = audit;
    }

    static void validateCommand(OpsOrderCancellationCommand command) {
        if (command == null || command.orderId() == null || command.orderId() <= 0
                || command.expectedOrderStatus() == null || command.expectedOrderVersion() == null
                || command.expectedOrderVersion() < 0 || command.reasonCode() == null
                || command.expectedShipment() == null
                || (command.expectedShipment() == OpsShipmentExpectation.EXPECTED_ABSENT
                    && command.expectedShipmentVersion() != null)
                || (command.expectedShipment() == OpsShipmentExpectation.EXPECTED_PRESENT
                    && (command.expectedShipmentVersion() == null || command.expectedShipmentVersion() < 0))) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
    }

    Order lockOrder(Long id) {
        Order order = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    /** Called only after Order and Shipment locks; governance mutations retain this Account lock too. */
    Long authorize() {
        Long actor = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        identities.requireActiveAccountForUpdate(actor);
        var persisted = roles.rolesFor(actor);
        if (!persisted.contains("ADMIN") && !persisted.contains("MANAGER")) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    static void requireCancellable(Order order, OpsOrderCancellationCommand command,
            Optional<ShipmentFulfillmentFacts> shipment) {
        if (order.getStatus() != command.expectedOrderStatus()
                || !Objects.equals(order.getVersion(), command.expectedOrderVersion())
                || (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.CONFIRMED)
                || order.getInventoryReleasedAt() != null || order.getCancelledAt() != null
                || order.getCancellationReason() != null || order.getDeliveredAt() != null
                || (order.getStatus() == OrderStatus.NEW && order.getReadyAt() != null)) throw conflict();
        if (command.expectedShipment() == OpsShipmentExpectation.EXPECTED_ABSENT) {
            if (shipment.isPresent()) throw conflict();
        } else if (shipment.isEmpty()
                || shipment.get().version() != command.expectedShipmentVersion()) throw conflict();
        if (shipment.isPresent()) {
            var facts = shipment.get();
            if (order.getStatus() != OrderStatus.CONFIRMED || order.getReadyAt() == null
                    || !order.getId().equals(facts.orderId()) || facts.shipmentId() == null || facts.shipmentId() <= 0
                    || facts.shipperId() == null || facts.shipperId() <= 0 || facts.version() < 0
                    || facts.status() != ShipmentStatus.ASSIGNED || facts.pickedUpAt() != null
                    || facts.deliveredAt() != null || facts.attemptCount() != 0 || facts.maxAttempts() != 2) throw conflict();
        }
    }

    void release(Order order, Instant at) {
        releaseInventory(order,at);
        vouchers.releaseForCancellation(order.getId());
    }

    void releaseInventory(Order order, Instant at) {
        if (order.getInventoryReleasedAt() != null) throw conflict();
        inventory.restore(items.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(item -> new StockQuantity(item.getProductId(), item.getQuantity())).toList());
        if (order.getStatus() == OrderStatus.SHIPPING) order.releaseInventoryAfterFailedDelivery(at);
        else order.releaseInventory(at);
        // System-wide Ops needs no vendor-owned Shop authorization or mutable Shop eligibility.
    }

    void releaseCancelledVoucher(Order order) {
        vouchers.releaseForCancelledUndeliveredOrder(order.getId());
    }

    void appendAudit(Order order, OrderStatus from, Long originalVersion, Long actor, OpsCancellationReason reason) {
        audit.append(new AuditEntry(actor, "ORDER_OPS_CANCELLED", "ORDER", order.getId(),
                Map.of("status", from.name(), "version", originalVersion.toString()),
                Map.of("status", order.getStatus().name(), "version", order.getVersion().toString()), reason.name()));
    }

    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }
}
