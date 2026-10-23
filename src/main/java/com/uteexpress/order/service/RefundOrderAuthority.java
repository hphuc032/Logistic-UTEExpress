package com.uteexpress.order.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.*;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.payment.dto.ManualRefundCommand;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Order-first locks; no Role row lock is taken after Account. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RefundOrderAuthority {
    private final OrderRepository orders;
    private final ReturnRequestRepository returns;
    private final OrderStatusHistoryRepository history;
    private final EntityManager entities;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final RoleGovernanceService roles;
    private final AuditLogService audit;
    public RefundOrderAuthority(OrderRepository orders, ReturnRequestRepository returns,
            OrderStatusHistoryRepository history, EntityManager entities, CurrentAccountIdProvider accounts,
            AccountIdentityService identities, RoleGovernanceService roles, AuditLogService audit) {
        this.orders = orders; this.returns = returns; this.history = history; this.entities = entities;
        this.accounts = accounts; this.identities = identities; this.roles = roles; this.audit = audit;
    }
    public record Locked(Order order, ReturnRequest request) { }
    public Locked lock(Long orderId) {
        var order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        entities.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        var request = returns.findByOrderIdForUpdate(orderId).orElse(null);
        if (request != null) entities.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        return new Locked(order, request);
    }
    public Long authorizeAdmin() {
        Long actor = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        // Own fresh READ_COMMITTED persistence context (OSIV disabled); no earlier Account load.
        identities.requireActiveAccountForUpdate(actor);
        if (!roles.rolesFor(actor).contains("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        return actor;
    }
    public void requireEligible(Locked locked, ManualRefundCommand command, Instant at) {
        var o = locked.order(); var r = locked.request();
        if (o.getStatus() != OrderStatus.RETURNED || !Objects.equals(o.getVersion(), command.expectedOrderVersion())
                || r == null || r.getStatus() != ReturnRequestStatus.APPROVED
                || !Objects.equals(r.getVersion(), command.expectedReturnRequestVersion())
                || !Objects.equals(r.getOrderId(), o.getId()) || !Objects.equals(r.getRequestedBy(), o.getBuyerId())
                || r.getReceivedAt() == null || r.getReceivedBy() == null || r.getReceivedBy() <= 0
                || r.getReceiptReference() == null || r.getReceiptReference().isBlank()
                || r.getDecisionAt() == null || r.getReceivedAt().isBefore(r.getDecisionAt()) || r.getReceivedAt().isAfter(at))
            throw new ApplicationException(ErrorCode.CONFLICT);
        // Receipt evidence must be tied to the persisted dedicated lifecycle receipt.
        boolean receipt = history.findByOrderIdOrderByCreatedAtAscIdAsc(o.getId()).stream().anyMatch(h ->
                h.getFromStatus() == OrderStatus.RETURN_REQUESTED && h.getToStatus() == OrderStatus.RETURNED
                && Objects.equals(h.getActorId(), r.getReceivedBy()) && h.getCreatedAt().equals(r.getReceivedAt()));
        if (!receipt) throw new ApplicationException(ErrorCode.CONFLICT);
    }
    public void audit(Long orderId, Long version, Long actor, Long refundId) {
        audit.append(new AuditEntry(actor, "ORDER_COD_REFUNDED", "ORDER", orderId,
                Map.of("status", "RETURNED", "version", version.toString()),
                Map.of("status", "REFUNDED", "version", Long.toString(version + 1), "relatedId", refundId.toString()),
                "MANUAL_COD_REFUND"));
    }
}
