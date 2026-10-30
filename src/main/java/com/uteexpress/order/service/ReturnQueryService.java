package com.uteexpress.order.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.*;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.service.VendorShopQueryService;
import java.time.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReturnQueryService {
    private final OrderRepository orders;
    private final ReturnRequestRepository returns;
    private final CurrentAccountIdProvider accounts;
    private final IdentityAuthenticationService identities;
    private final VendorShopQueryService shops;
    private final Clock clock;

    public ReturnQueryService(OrderRepository orders, ReturnRequestRepository returns,
            CurrentAccountIdProvider accounts, IdentityAuthenticationService identities,
            VendorShopQueryService shops, Clock clock) {
        this.orders = orders; this.returns = returns; this.accounts = accounts;
        this.identities = identities; this.shops = shops; this.clock = clock;
    }

    @PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
            + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public ReturnManagementView buyer(Long id) {
        requireId(id);
        Long buyer = actor(false);
        // Read return evidence only after the authoritative parent ownership query succeeds.
        return view(orders.findByIdAndBuyerId(id, buyer).orElseThrow(ReturnQueryService::missing), false);
    }

    @PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
    public ReturnManagementView vendor(Long id) {
        requireId(id);
        Long shop = shops.requireApprovedOwnedShop(actor(true)).shopId();
        return view(orders.findByIdAndShopId(id, shop).orElseThrow(ReturnQueryService::missing), true);
    }

    private ReturnManagementView view(Order order, boolean vendor) {
        var request = returns.findByOrderId(order.getId()).map(r -> new ReturnRequestView(
                r.getId(), r.getStatus(), r.getReason(), r.getRequestedAt(),
                r.getDecisionAt(), r.getDecisionNote(), r.getVersion(),
                r.getReceivedAt(), r.getReceiptReference())).orElse(null);
        Instant delivered = order.getDeliveredAt();
        return new ReturnManagementView(order.getId(), order.getStatus(), order.getVersion(), delivered,
                delivered == null ? null : delivered.plus(Duration.ofDays(7)),
                !vendor && order.getStatus() == OrderStatus.DELIVERED && request == null
                    && ReturnOrderAuthority.withinWindow(delivered, clock.instant()),
                vendor && order.getStatus() == OrderStatus.RETURN_REQUESTED && request != null
                    && request.status() == ReturnRequestStatus.PENDING,
                vendor && order.getStatus() == OrderStatus.RETURN_REQUESTED && request != null
                    && request.status() == ReturnRequestStatus.APPROVED && request.receivedAt() == null, request);
    }

    private Long actor(boolean vendor) {
        Long actor = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        var persisted = identities.findByUserId(actor).orElseThrow(ReturnQueryService::missing);
        if (!persisted.active() || !(vendor ? persisted.roleCodes().contains("VENDOR")
                : persisted.roleCodes().contains("USER") || persisted.roleCodes().contains("VENDOR")))
            throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        return actor;
    }
    private static void requireId(Long id) { if (id == null || id <= 0) throw missing(); }
    private static ApplicationException missing() { return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND); }
}
