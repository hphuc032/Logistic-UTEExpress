package com.uteexpress.order.service;

import com.uteexpress.checkout.dto.CheckoutQuote;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderAction;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.dto.OrderStatusChangedEvent;
import com.uteexpress.order.dto.OrderTransitionCommand;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderStatusHistoryRepository;
import com.uteexpress.security.CurrentUser;
import com.uteexpress.security.CurrentUserProvider;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;

import com.uteexpress.order.entity.Order;
import com.uteexpress.order.entity.OrderItem;
import com.uteexpress.order.entity.OrderStatusHistory;

/**
 * Shared lifecycle orchestration. Production vendor guards are supplied by the
 * placement/lifecycle bean; other workflow integrations continue to deny by default.
 */
public class OrderLifecycleServiceImpl implements OrderLifecycleService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final CurrentUserProvider users;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OrderLifecycleServiceImpl(OrderRepository orders,
            OrderItemRepository items,
            OrderStatusHistoryRepository history,
            CurrentUserProvider users,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher events, Clock clock) {
        this.orders = Objects.requireNonNull(orders);
        this.items = Objects.requireNonNull(items);
        this.history = Objects.requireNonNull(history);
        this.users = Objects.requireNonNull(users);
        this.transactions = new TransactionTemplate(transactionManager);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Later server integration must verify identity via HP, ownership/scope and ALL
     * action-specific guards from ORD-00, locking dependent evidence in this transaction.
     * Return the persisted actor ID and a safe history reason only after authorization.
     * Never copy address/payment data or unchecked browser text into the reason.
     * No subject parsing here.
     */
    protected Authorization authorize(CurrentUser user, Order order,
            OrderTransitionCommand command) {
        throw new ApplicationException(
                ErrorCode.ACCESS_DENIED);
    }

    /** Trusted guard output, not an HTTP command or an identity resolver. */
    public record Authorization(Long actorId, String reason) { }

    protected Order loadForMutation(Long id) {
        return orders.findById(id).orElseThrow(() -> new ApplicationException(ErrorCode.ACCESS_DENIED));
    }

    protected void beforeValidatedTransition(Order order, OrderTransitionCommand command, Instant at) { }

    /** Return integration stays separate from vendor cancellation effects. */
    protected ReturnOrderAuthority returnAuthority() {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    @Override
    public OrderStatusChangedEvent requestReturn(com.uteexpress.order.dto.RequestReturnCommand command) {
        ReturnOrderAuthority.validate(command);
        return returnTransaction(() -> {
            var authority = returnAuthority();
            var guarded = authority.lockBuyer(command.orderId());
            // Compare the actual UTC instant before rounding stored timestamps to PostgreSQL precision.
            var now = clock.instant();
            authority.requireRequestable(guarded, command.expectedVersion(), now);
            var at = now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            authority.create(guarded, command.reason(), at);
            return persistValidatedTransition(guarded.order(),
                    OrderTransitionPolicy.requireTarget(guarded.order().getStatus(), OrderAction.REQUEST_RETURN),
                    guarded.actorId(), at, "Buyer requested return");
        });
    }

    @Override
    public void approveReturn(com.uteexpress.order.dto.ReturnDecisionCommand command) {
        decideReturn(command, true);
    }

    @Override
    public OrderStatusChangedEvent rejectReturn(com.uteexpress.order.dto.ReturnDecisionCommand command) {
        return decideReturn(command, false);
    }

    private OrderStatusChangedEvent decideReturn(com.uteexpress.order.dto.ReturnDecisionCommand command,
            boolean approve) {
        ReturnOrderAuthority.validate(command, approve);
        return returnTransaction(() -> {
            var authority = returnAuthority();
            var guarded = authority.lockVendor(command.orderId());
            authority.requirePending(guarded, command);
            var at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            authority.decide(guarded, approve, command.reason(), at);
            if (approve) return null; // Evidence only: Order/version/history/events stay untouched.
            return persistValidatedTransition(guarded.order(),
                    OrderTransitionPolicy.requireTarget(guarded.order().getStatus(), OrderAction.REJECT_RETURN),
                    // Keep free-text decision evidence in its owned projection, out of lifecycle consumers.
                    guarded.actorId(), at, "Vendor rejected return");
        });
    }

    @Override
    public OrderStatusChangedEvent receiveReturn(com.uteexpress.order.dto.ReceiveReturnCommand command) {
        ReturnOrderAuthority.validate(command);
        return returnTransaction(() -> {
            var authority = returnAuthority();
            var guarded = authority.lockVendor(command.orderId());
            authority.requireReceivable(guarded, command);
            var at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            authority.receive(guarded, command.receiptReference(), at);
            return persistValidatedTransition(guarded.order(),
                    OrderTransitionPolicy.requireTarget(guarded.order().getStatus(), OrderAction.RECEIVE_RETURN),
                    guarded.actorId(), at, "Vendor attested return receipt");
        });
    }

    private <T> T returnTransaction(java.util.function.Supplier<T> operation) {
        // Never join or suspend a coordinator which might already hold Account/Shop locks.
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        try {
            return transactions.execute(tx -> operation.get());
        } catch (OptimisticLockingFailureException | OptimisticLockException
                | org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    protected Order loadForOpsMutation(Long id) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    /** QD dependency: lock/read in this transaction, without taking any Account lock. */
    protected java.util.Optional<com.uteexpress.shipping.dto.ShipmentFulfillmentFacts> lockShipmentForOps(
            Order order, com.uteexpress.order.dto.OpsOrderCancellationCommand command) {
        // The production lifecycle bean supplies Shipping's authoritative lock/read boundary.
        throw new ApplicationException(ErrorCode.CONFLICT);
    }

    protected Long authorizeOps(Order order) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    /** QD dependency: resolve the previously locked, validated assignment and append Shipment audit. */
    protected void resolveShipmentForOps(Order order,
            java.util.Optional<com.uteexpress.shipping.dto.ShipmentFulfillmentFacts> shipment, Instant at) {
        throw new ApplicationException(ErrorCode.CONFLICT);
    }

    protected void beforeOpsCancellation(Order order, Instant at) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    protected void auditOpsCancellation(Order order, OrderStatus from, Long originalVersion,
            Long actor, com.uteexpress.order.dto.OpsCancellationReason reason) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    @Override
    public OrderStatusChangedEvent cancelForOps(com.uteexpress.order.dto.OpsOrderCancellationCommand command) {
        OpsOrderAuthority.validateCommand(command);
        // Joining cannot repair an Account lock held by an outer coordinator. Never suspend it
        // with REQUIRES_NEW either: that could wait on locks owned by the suspended caller.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        try {
            return transactions.execute(tx -> {
                var user = currentUser();
                if (!user.authorities().contains("ROLE_ADMIN") && !user.authorities().contains("ROLE_MANAGER")) {
                    throw new ApplicationException(ErrorCode.ACCESS_DENIED);
                }
                Order order = loadForOpsMutation(command.orderId());
                var shipment = Objects.requireNonNull(lockShipmentForOps(order, command));
                Long actor = requireHumanActor(authorizeOps(order));
                OpsOrderAuthority.requireCancellable(order, command, shipment);
                OrderStatus from = order.getStatus();
                Long version = order.getVersion();
                var action = from == OrderStatus.NEW ? OrderAction.CANCEL_NEW : OrderAction.CANCEL_CONFIRMED;
                var target = OrderTransitionPolicy.requireTarget(from, action);
                Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                resolveShipmentForOps(order, shipment, at);
                beforeOpsCancellation(order, at);
                var event = persistValidatedTransition(order, target, actor, at, command.reasonCode().description());
                auditOpsCancellation(order, from, version, actor, command.reasonCode());
                return event;
            });
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    protected void authorizeReady(CurrentUser user, Order order) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    @Override
    public void markReady(com.uteexpress.order.dto.OrderReadyCommand command) {
        if (command == null || command.orderId() == null || command.orderId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0) {
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        }
        try {
            transactions.executeWithoutResult(tx -> {
                var user = currentUser();
                var order = loadForMutation(command.orderId());
                authorizeReady(user, order);
                if (!Objects.equals(order.getVersion(), command.expectedVersion())) {
                    throw new ApplicationException(ErrorCode.CONFLICT);
                }
                order.markReady(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
                orders.saveAndFlush(order);
            });
        } catch (OptimisticLockingFailureException | OptimisticLockException ex) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    /** Resolve the persisted buyer ID from trusted identity and verify server-owned facts; deny until integrated. */
    protected Long authorizeCreation(CurrentUser user,
            CheckoutQuote quote) {
        throw new ApplicationException(
                ErrorCode.ACCESS_DENIED);
    }

    /**
     * Internal domain-core persistence hook, not the public checkout workflow.
     * Buyer identity comes only from trusted authorization, never a browser buyerId.
     * Checkout, inventory, vouchers, payments and replay remain deferred.
     */
    public Order createNew(String orderCode, String checkoutKey, String requestHash,
            CheckoutQuote trustedQuote) {
        return transactions.execute(tx -> {
            Long actorId = requireHumanActor(authorizeCreation(currentUser(), trustedQuote));
            return persistNew(actorId, orderCode, checkoutKey, requestHash, trustedQuote, clock.instant());
        });
    }

    /** Lifecycle creation primitive for trusted placement, after identity and checkout validation. */
    protected final Order persistNew(Long actorId, String orderCode, String checkoutKey, String requestHash,
            CheckoutQuote trustedQuote, Instant at) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Order creation requires the placement transaction");
        }
        requireHumanActor(actorId);
        Order order = new Order(actorId, orderCode, checkoutKey, requestHash, trustedQuote, at);
        orders.saveAndFlush(order);
        items.saveAllAndFlush(trustedQuote.items().stream()
                .map(snapshot -> new OrderItem(order.getId(), snapshot)).toList());
        history.saveAndFlush(new OrderStatusHistory(order.getId(), null, order.getStatus(), actorId, at, null));
        return order;
    }

    @Override
    public OrderStatusChangedEvent transition(
            OrderTransitionCommand command) {
        if (command == null || command.orderId() == null || command.orderId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || command.expectedStatus() == null || command.action() == null) {
            throw new ApplicationException(
                    ErrorCode.INVALID_REQUEST);
        }
        // Receipt always needs the dedicated two-version, persisted-evidence boundary.
        if (command.action() == OrderAction.RECEIVE_RETURN)
            throw new ApplicationException(ErrorCode.CONFLICT);
        try {
            return transactions.execute(tx -> {
                var user = currentUser();
                if (command.action() == OrderAction.EXPIRE_PAYMENT) {
                    throw new ApplicationException(
                            ErrorCode.ACCESS_DENIED);
                }
                Order order = loadForMutation(command.orderId());
                // Authorize before revealing state/version of a potentially foreign order.
                Authorization authorization = Objects.requireNonNull(authorize(user, order, command));
                Long actorId = requireHumanActor(authorization.actorId());
                if (order.getStatus() != command.expectedStatus()
                        || !Objects.equals(order.getVersion(), command.expectedVersion())) {
                    throw new ApplicationException(
                            ErrorCode.CONFLICT);
                }
                var from = order.getStatus();
                var to = OrderTransitionPolicy.requireTarget(from, command.action());
                Instant at = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                // Only the trusted guard's safe reason reaches history and external consumers.
                String reason = authorization.reason();
                if (reason != null) { reason = reason.trim(); }
                if ((command.action() == OrderAction.CANCEL_CONFIRMED
                        || command.action() == OrderAction.CANCEL_FAILED_DELIVERY
                        || command.action() == OrderAction.REJECT_RETURN)
                        && (reason == null || reason.isBlank())) {
                    throw new ApplicationException(ErrorCode.CONFLICT);
                }
                beforeValidatedTransition(order, command, at);
                return persistValidatedTransition(order, to, actorId, at, reason);
            });
        } catch (OptimisticLockingFailureException
                | OptimisticLockException ex) {
            throw new ApplicationException(
                    ErrorCode.CONFLICT);
        }
    }

    /** Shared writer after action-specific authorization and guards, inside the caller's transaction. */
    protected final OrderStatusChangedEvent persistValidatedTransition(Order order, OrderStatus to,
            Long actorId, Instant at, String reason) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Order transition requires the lifecycle transaction");
        }
        requireHumanActor(actorId);
        var from = order.getStatus();
        order.applyValidatedTransition(to, at, reason);
        orders.saveAndFlush(order);
        history.saveAndFlush(new OrderStatusHistory(order.getId(), from, to, actorId, at, reason));
        var event = new OrderStatusChangedEvent(UUID.randomUUID(), order.getId(), from, to, actorId, at, reason);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { events.publishEvent(event); }
        });
        return event;
    }

    @Override
    public void prepareShipperTransition(Long orderId, Long expectedOrderVersion,
            Long expectedShipmentVersion, OrderAction action) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    @Override
    public OrderStatusChangedEvent completeShipperTransition(Long orderId, Long expectedOrderVersion,
            Long expectedShipmentVersion, OrderAction action) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    private CurrentUser currentUser() {
        return users.currentUser().orElseThrow(() -> new ApplicationException(
                ErrorCode.UNAUTHENTICATED));
    }

    @Override
    public void prepareShipmentOnlyOperation(Long orderId, Long expectedOrderVersion,
            Long expectedShipmentVersion, com.uteexpress.shipping.dto.DeliveryAttemptOperation operation) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    @Override
    public void verifyShipmentOnlyOperation(Long orderId, Long expectedOrderVersion,
            Long expectedShipmentVersion, com.uteexpress.shipping.dto.DeliveryAttemptOperation operation) {
        throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    /** Human paths require a persisted actor; this is not a system-actor validator. */
    private static Long requireHumanActor(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new ApplicationException(
                    ErrorCode.ACCESS_DENIED);
        }
        return actorId;
    }

    @Override
    public OrderStatusChangedEvent expireUnpaidOnlineOrder(
            Long orderId, Long expectedVersion) {
        // Deferred trusted system boundary: future verified expiry writes null actor history,
        // without requireHumanActor. Requires payment-callback serialization; no scheduler yet.
        throw new ApplicationException(
                ErrorCode.ACCESS_DENIED);
    }
}
