package com.uteexpress.order.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.service.VendorShopQueryService;
import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Order-internal guards/evidence; the lifecycle owns every Order status write. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ReturnOrderAuthority {
    private final OrderRepository orders;
    private final ReturnRequestRepository returns;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final IdentityAuthenticationService authentication;
    private final VendorShopQueryService shops;
    private final EntityManager entityManager;

    public ReturnOrderAuthority(OrderRepository orders, ReturnRequestRepository returns,
            CurrentAccountIdProvider accounts, AccountIdentityService identities,
            IdentityAuthenticationService authentication, VendorShopQueryService shops, EntityManager entityManager) {
        this.orders = orders; this.returns = returns; this.accounts = accounts; this.identities = identities;
        this.authentication = authentication; this.shops = shops; this.entityManager = entityManager;
    }

    public record LockedReturn(Order order, ReturnRequest request, Long actorId) { }

    public LockedReturn lockBuyer(Long id) {
        Long buyer = actor();
        Order order = orders.findByIdAndBuyerIdForUpdate(id, buyer).orElseThrow(ReturnOrderAuthority::missing);
        return lockEvidenceAndEligibility(order, buyer, false);
    }

    public LockedReturn lockVendor(Long id) {
        Long vendor = actor();
        // Ownership selection only: this read acquires neither Account nor Shop row locks.
        Long shop = shops.requireApprovedOwnedShop(vendor).shopId();
        Order order = orders.findByIdAndShopIdForUpdate(id, shop).orElseThrow(ReturnOrderAuthority::missing);
        var guarded = lockEvidenceAndEligibility(order, vendor, true);
        if (!shops.requireApprovedOwnedShopForUpdate(vendor).shopId().equals(order.getShopId())) throw missing();
        return guarded;
    }

    private LockedReturn lockEvidenceAndEligibility(Order order, Long actor, boolean vendor) {
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        var request = returns.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (request != null) entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        // Order -> ReturnRequest -> Account -> Shop, retained through lifecycle commit.
        identities.requireActiveAccountForUpdate(actor);
        var persisted = authentication.findByUserId(actor).orElseThrow(ReturnOrderAuthority::missing);
        boolean allowed = vendor ? persisted.roleCodes().contains("VENDOR")
                : persisted.roleCodes().contains("USER") || persisted.roleCodes().contains("VENDOR");
        if (!persisted.active() || !allowed) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        return new LockedReturn(order, request, actor);
    }

    public void requireRequestable(LockedReturn guarded, Long version, Instant now) {
        if (!Objects.equals(version, guarded.order().getVersion()) || guarded.request() != null
                || guarded.order().getStatus() != OrderStatus.DELIVERED
                || !withinWindow(guarded.order().getDeliveredAt(), now)) throw conflict();
    }

    public static boolean withinWindow(Instant delivered, Instant now) {
        return delivered != null && !now.isBefore(delivered)
                && !now.isAfter(delivered.plus(Duration.ofDays(7)));
    }

    public void create(LockedReturn guarded, String reason, Instant at) {
        returns.saveAndFlush(new ReturnRequest(guarded.order().getId(), guarded.actorId(), reason, at));
    }

    public void requirePending(LockedReturn guarded, ReturnDecisionCommand command) {
        if (guarded.order().getStatus() != OrderStatus.RETURN_REQUESTED
                || !Objects.equals(guarded.order().getVersion(), command.expectedVersion())
                || guarded.request() == null || guarded.request().getStatus() != ReturnRequestStatus.PENDING
                || !Objects.equals(guarded.request().getVersion(), command.expectedRequestVersion())
                || !guarded.request().getRequestedBy().equals(guarded.order().getBuyerId())
                || !withinWindow(guarded.order().getDeliveredAt(), guarded.request().getRequestedAt())) throw conflict();
    }

    public void decide(LockedReturn guarded, boolean approve, String reason, Instant at) {
        guarded.request().decide(approve, guarded.actorId(), reason, at);
        returns.saveAndFlush(guarded.request());
    }

    public void requireReceivable(LockedReturn guarded, ReceiveReturnCommand command) {
        var request = guarded.request();
        if (guarded.order().getStatus() != OrderStatus.RETURN_REQUESTED
                || !Objects.equals(guarded.order().getVersion(), command.expectedVersion())
                || request == null || request.getStatus() != ReturnRequestStatus.APPROVED
                || !Objects.equals(request.getOrderId(), guarded.order().getId())
                || !Objects.equals(request.getRequestedBy(), guarded.order().getBuyerId())
                || !Objects.equals(request.getVersion(), command.expectedRequestVersion())
                || request.getReceivedAt() != null || request.getReceivedBy() != null
                || request.getReceiptReference() != null) throw conflict();
    }

    public void receive(LockedReturn guarded, String reference, Instant at) {
        guarded.request().receive(guarded.actorId(), reference, at);
        returns.saveAndFlush(guarded.request());
    }

    static void validate(ReceiveReturnCommand command) {
        if (command == null) throw invalid();
        validateSelection(command.orderId(), command.expectedVersion());
        if (command.expectedRequestVersion() == null || command.expectedRequestVersion() < 0) throw invalid();
        ReturnRequest.requireReceiptReference(command.receiptReference());
    }

    static void validate(RequestReturnCommand command) {
        if (command == null) throw invalid();
        validateSelection(command.orderId(), command.expectedVersion());
        ReturnRequest.requireReason(command.reason());
    }

    static void validate(ReturnDecisionCommand command, boolean approve) {
        if (command == null) throw invalid();
        validateSelection(command.orderId(), command.expectedVersion());
        if (command.expectedRequestVersion() == null || command.expectedRequestVersion() < 0) throw invalid();
        if (approve) {
            if (command.reason() != null) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        } else ReturnRequest.requireReason(command.reason());
    }

    private static void validateSelection(Long id, Long version) {
        if (id == null || id <= 0) throw missing();
        if (version == null || version < 0) throw invalid();
    }

    private Long actor() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }
    private static ApplicationException invalid() { return new ApplicationException(ErrorCode.INVALID_REQUEST); }
    private static ApplicationException missing() { return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND); }
    private static ApplicationException conflict() { return new ApplicationException(ErrorCode.CONFLICT); }
}
