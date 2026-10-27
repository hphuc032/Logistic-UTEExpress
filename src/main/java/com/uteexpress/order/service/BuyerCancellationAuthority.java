package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.order.dto.BuyerCancellationCommand;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Buyer guards only. Lifecycle owns status/history and composes existing cancellation effects. */
@Service
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).USER.authority())")
@Transactional(propagation = Propagation.MANDATORY)
public class BuyerCancellationAuthority {
    private final ObjectProvider<OrderRepository> orders;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final IdentityAuthenticationService authentication;
    private final ObjectProvider<EntityManager> entities;

    public BuyerCancellationAuthority(ObjectProvider<OrderRepository> orders, CurrentAccountIdProvider accounts,
            AccountIdentityService identities, IdentityAuthenticationService authentication, ObjectProvider<EntityManager> entities) {
        this.orders=orders; this.accounts=accounts; this.identities=identities;
        this.authentication=authentication; this.entities=entities;
    }

    public record LockedBuyerOrder(Order order, Long actorId) { }

    public LockedBuyerOrder lock(Long orderId) {
        Long buyer=accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        // Scope before exposing state/version. Order -> Account locks are retained through commit.
        // Match OrderEligibilityService: persistence-free HTTP test contexts fail closed on use.
        Order order=orders.getObject().findByIdAndBuyerIdForUpdate(orderId,buyer)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entities.getObject().refresh(order,LockModeType.PESSIMISTIC_WRITE);
        if(!Objects.equals(order.getBuyerId(),buyer)) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        identities.requireActiveAccountForUpdate(buyer);
        var persisted=authentication.findByUserId(buyer)
                .orElseThrow(() -> new ApplicationException(ErrorCode.ACCESS_DENIED));
        if(!persisted.active() || !persisted.roleCodes().contains("USER"))
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        return new LockedBuyerOrder(order,buyer);
    }

    public void requireCancellable(LockedBuyerOrder locked, Long version) {
        Order order=locked.order();
        if(order.getStatus()!=OrderStatus.NEW || !Objects.equals(order.getVersion(),version)
                || order.getInventoryReleasedAt()!=null || order.getCancelledAt()!=null || order.getDeliveredAt()!=null)
            throw new ApplicationException(ErrorCode.CONFLICT);
    }

    static void validate(BuyerCancellationCommand command) {
        if(command==null || command.expectedOrderVersion()==null || command.expectedOrderVersion()<0)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        if(command.orderId()==null || command.orderId()<=0)
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
