package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.BuyerOrderDetail;
import com.uteexpress.order.dto.BuyerOrderPage;
import com.uteexpress.order.dto.BuyerOrderSummary;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.order.repository.OrderStatusHistoryRepository;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).USER.authority(), "
        + "T(com.uteexpress.security.RoleCode).VENDOR.authority())")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class BuyerOrderService {
    public static final int MAX_SIZE = 100;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final PaymentReadService payments;
    private final CurrentAccountIdProvider accounts;

    public BuyerOrderService(OrderRepository orders, OrderItemRepository items,
            OrderStatusHistoryRepository history, PaymentReadService payments, CurrentAccountIdProvider accounts) {
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.payments = payments;
        this.accounts = accounts;
    }

    public BuyerOrderPage list(int page, int size) {
        Long buyer = buyerId();
        // JPA offsets are signed integers. Check the product before PageRequest reaches Hibernate.
        if (page < 0 || size < 1 || size > MAX_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        var result = orders.findByBuyerId(buyer, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        var content = result.getContent().stream().map(order -> new BuyerOrderSummary(order.getId(),
                order.getOrderCode(), order.getShopId(), order.getStatus(), order.getGrandTotal(),
                order.getCreatedAt())).toList();
        return new BuyerOrderPage(content, page, size, result.getTotalElements(), result.getTotalPages(),
                page > 0, (long) page + 1 < result.getTotalPages());
    }

    public BuyerOrderDetail detail(Long id) {
        Long buyer = buyerId();
        if (id == null || id <= 0) throw notFound();
        // Never perform a global order lookup. Children are read only after this ownership check.
        var order = orders.findByIdAndBuyerId(id, buyer).orElseThrow(BuyerOrderService::notFound);
        var lines = items.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(item -> new BuyerOrderDetail.Item(item.getProductId(), item.getProductNameSnapshot(),
                        item.getUnitPrice(), item.getDiscountSnapshot(), item.getFinalUnitPrice(),
                        item.getQuantity(), item.getLineTotal())).toList();
        var timeline = history.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                .map(entry -> new BuyerOrderDetail.TimelineEntry(entry.getFromStatus(), entry.getToStatus(),
                        entry.getCreatedAt())).toList();
        // Repeatable-read keeps status, history and payment facts from the same database snapshot.
        // Missing history/payment rows remain empty; createdAt/updatedAt never synthesize events.
        return new BuyerOrderDetail(order.getId(), order.getOrderCode(), order.getShopId(), order.getStatus(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getDeliveredAt(), order.getCancelledAt(),
                new BuyerOrderDetail.Address(order.getReceiverName(), order.getPhone(), order.getProvinceCode(),
                        order.getDistrict(), order.getDetail()), order.getSubtotal(), order.getDiscountTotal(),
                order.getShippingFee(), order.getGrandTotal(), lines, timeline, payments.recordsForOrder(order.getId()));
    }

    private Long buyerId() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
