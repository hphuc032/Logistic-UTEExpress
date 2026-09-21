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
 * Application orchestration for ORD-01. Deliberately unannotated until HP identity
 * and QD guard integrations exist. All authorization hooks deny by default.
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
            Instant at = clock.instant();
            Order order = new Order(actorId, orderCode, checkoutKey, requestHash, trustedQuote, at);
            orders.saveAndFlush(order);
            items.saveAllAndFlush(trustedQuote.items().stream()
                    .map(snapshot -> new OrderItem(order.getId(), snapshot)).toList());
            history.saveAndFlush(new OrderStatusHistory(order.getId(), null, order.getStatus(), actorId, at, null));
            return order;
        });
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
        try {
            return transactions.execute(tx -> {
                var user = currentUser();
                if (command.action() == OrderAction.EXPIRE_PAYMENT) {
                    throw new ApplicationException(
                            ErrorCode.ACCESS_DENIED);
                }
                Order order = orders.findById(command.orderId()).orElseThrow(() ->
                        new ApplicationException(
                                ErrorCode.ACCESS_DENIED));
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
                Instant at = clock.instant();
                // Only the trusted guard's safe reason reaches history and external consumers.
                String reason = authorization.reason();
                if (reason != null) { reason = reason.trim(); }
                if ((command.action() == OrderAction.CANCEL_CONFIRMED
                        || command.action() == OrderAction.CANCEL_FAILED_DELIVERY
                        || command.action() == OrderAction.REJECT_RETURN)
                        && (reason == null || reason.isBlank())) {
                    throw new ApplicationException(ErrorCode.CONFLICT);
                }
                order.applyValidatedTransition(to, at, reason);
                orders.saveAndFlush(order);
                history.saveAndFlush(new OrderStatusHistory(order.getId(), from, to, actorId, at, reason));
                var event = new OrderStatusChangedEvent(UUID.randomUUID(),
                        order.getId(), from, to, actorId, at, reason);
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override public void afterCommit() { events.publishEvent(event); }
                        });
                return event;
            });
        } catch (OptimisticLockingFailureException
                | OptimisticLockException ex) {
            throw new ApplicationException(
                    ErrorCode.CONFLICT);
        }
    }

    private CurrentUser currentUser() {
        return users.currentUser().orElseThrow(() -> new ApplicationException(
                ErrorCode.UNAUTHENTICATED));
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
