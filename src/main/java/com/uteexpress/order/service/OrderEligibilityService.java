package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.dto.ReviewableOrderItem;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderEligibilityService implements OrderEligibilityQueryService, OrderReviewWriteBoundary {
    private final ObjectProvider<OrderRepository> orders;
    private final ObjectProvider<OrderItemRepository> items;
    private final CurrentAccountIdProvider accounts;
    private final IdentityAuthenticationService authentication;
    private final AccountIdentityService identities;
    private final ObjectProvider<EntityManager> entities;
    private final ObjectProvider<PlatformTransactionManager> managers;

    public OrderEligibilityService(ObjectProvider<OrderRepository> orders, ObjectProvider<OrderItemRepository> items,
            CurrentAccountIdProvider accounts, IdentityAuthenticationService authentication,
            AccountIdentityService identities, ObjectProvider<EntityManager> entities,
            ObjectProvider<PlatformTransactionManager> managers) {
        this.orders = orders;
        this.items = items;
        this.accounts = accounts;
        this.authentication = authentication;
        this.identities = identities;
        this.entities = entities;
        this.managers = managers;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCurrentBuyerEligibleToReview(Long orderItemId) {
        Long buyer = actor();
        requireBuyer(buyer);
        return validId(orderItemId) && orders.getObject().existsDeliveredItemForBuyer(orderItemId, buyer);
    }

    @Override
    public <T> T withEligibleCurrentBuyerItem(Long orderItemId, Function<ReviewableOrderItem, T> write) {
        // Joining/suspending a caller which already holds Account or Review inverts the lock order.
        if (TransactionSynchronizationManager.isActualTransactionActive() || write == null)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        Long buyer = actor();
        if (!validId(orderItemId)) throw missing();
        // HTTP/security-only contexts omit persistence. Missing providers fail closed on use.
        var transactions = new TransactionTemplate(managers.getObject());
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transactions.execute(tx -> {
            // This selection takes no row lock and is not an eligibility decision.
            var repository = orders.getObject();
            Long orderId = repository.findOwnedOrderIdForItem(orderItemId, buyer)
                    .orElseThrow(OrderEligibilityService::missing);
            var order = repository.findByIdAndBuyerIdForUpdate(orderId, buyer)
                    .orElseThrow(OrderEligibilityService::missing);
            // Also refresh a possible entity cached by OpenEntityManagerInView.
            entities.getObject().refresh(order, LockModeType.PESSIMISTIC_WRITE);
            if (!buyer.equals(order.getBuyerId())) throw missing();
            var item = items.getObject().findByIdAndOrderId(orderItemId, order.getId())
                    .orElseThrow(OrderEligibilityService::missing);
            // Order -> Account -> Review. The callback runs in this same transaction.
            identities.requireActiveAccountForUpdate(buyer);
            requireBuyer(buyer);
            if (order.getStatus() != OrderStatus.DELIVERED)
                throw new ApplicationException(ErrorCode.CONFLICT);
            return write.apply(new ReviewableOrderItem(item.getId(), order.getId(), buyer, item.getProductId()));
        });
    }

    private Long actor() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private void requireBuyer(Long buyer) {
        var account = authentication.findByUserId(buyer)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ACCESS_DENIED));
        if (!account.active() || !(account.roleCodes().contains("USER") || account.roleCodes().contains("VENDOR")))
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
    }

    private static boolean validId(Long id) { return id != null && id > 0; }
    private static ApplicationException missing() { return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND); }
}
