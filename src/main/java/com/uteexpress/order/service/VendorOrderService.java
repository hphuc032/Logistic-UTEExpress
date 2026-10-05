package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.AccountIdentityService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shop.service.VendorShopQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAuthority(T(com.uteexpress.security.RoleCode).VENDOR.authority())")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class VendorOrderService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final CurrentAccountIdProvider accounts;
    private final AccountIdentityService identities;
    private final VendorShopQueryService shops;
    private final PaymentReadService payments;

    public VendorOrderService(OrderRepository orders, OrderItemRepository items, OrderStatusHistoryRepository history,
            CurrentAccountIdProvider accounts, AccountIdentityService identities, VendorShopQueryService shops,
            PaymentReadService payments) {
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.accounts = accounts;
        this.identities = identities;
        this.shops = shops;
        this.payments = payments;
    }

    public VendorOrderPage list(int page, int size) {
        Long shop = shopId();
        if (page < 0 || size < 1 || size > BuyerOrderService.MAX_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        var result = orders.findByShopId(shop, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        var content = result.getContent().stream().map(order -> new BuyerOrderSummary(order.getId(),
                order.getOrderCode(), order.getShopId(), order.getStatus(), order.getGrandTotal(), order.getCreatedAt())).toList();
        return new VendorOrderPage(content, page, size, result.getTotalElements(), result.getTotalPages(),
                page > 0, (long) page + 1 < result.getTotalPages());
    }

    public VendorOrderDetail detail(Long id) {
        Long shop = shopId();
        if (id == null || id <= 0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        var order = orders.findByIdAndShopId(id, shop)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        var lines = items.findByOrderIdOrderByIdAsc(id).stream()
                .map(item -> new BuyerOrderDetail.Item(item.getProductId(), item.getProductNameSnapshot(),
                        item.getUnitPrice(), item.getDiscountSnapshot(), item.getFinalUnitPrice(),
                        item.getQuantity(), item.getLineTotal())).toList();
        var timeline = history.findByOrderIdOrderByCreatedAtAscIdAsc(id).stream()
                .map(entry -> new BuyerOrderDetail.TimelineEntry(entry.getFromStatus(), entry.getToStatus(), entry.getCreatedAt())).toList();
        var facts = new BuyerOrderDetail(id, order.getOrderCode(), shop, order.getStatus(), order.getCreatedAt(),
                order.getUpdatedAt(), order.getDeliveredAt(), order.getCancelledAt(),
                new BuyerOrderDetail.Address(order.getReceiverName(), order.getPhone(), order.getProvinceCode(),
                        order.getDistrict(), order.getDetail()), order.getSubtotal(), order.getDiscountTotal(),
                order.getShippingFee(), order.getGrandTotal(), lines, timeline, payments.recordsForOrder(id));
        return new VendorOrderDetail(facts, order.getVersion(), order.getReadyAt());
    }

    private Long shopId() {
        Long vendor = accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
        identities.getProfile(vendor);
        return shops.requireApprovedOwnedShop(vendor).shopId();
    }
}
