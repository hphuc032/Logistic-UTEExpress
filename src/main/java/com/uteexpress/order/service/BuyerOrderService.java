package com.uteexpress.order.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.identity.service.IdentityAuthenticationService;
import com.uteexpress.order.dto.BuyerOrderDetail;
import com.uteexpress.order.dto.BuyerOrderPage;
import com.uteexpress.order.dto.BuyerOrderPageDetail;
import com.uteexpress.order.dto.BuyerOrderSummary;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.order.entity.Order;
import com.uteexpress.order.repository.OrderItemRepository;
import com.uteexpress.order.repository.OrderRepository;
import com.uteexpress.order.repository.OrderStatusHistoryRepository;
import com.uteexpress.payment.service.PaymentReadService;
import com.uteexpress.security.CurrentUserProvider;
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
    private final CurrentUserProvider currentUsers;
    private final IdentityAuthenticationService identities;

    public BuyerOrderService(OrderRepository orders, OrderItemRepository items,
            OrderStatusHistoryRepository history, PaymentReadService payments, CurrentAccountIdProvider accounts,
            CurrentUserProvider currentUsers, IdentityAuthenticationService identities) {
        this.orders = orders;
        this.items = items;
        this.history = history;
        this.payments = payments;
        this.accounts = accounts;
        this.currentUsers = currentUsers;
        this.identities = identities;
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
        return detail(ownedOrder(id, buyerId()));
    }

    public BuyerOrderPageDetail pageDetail(Long id) {
        Long buyer = buyerId();
        var order = ownedOrder(id, buyer);
        Long cancellationVersion = null;
        // One read snapshot supplies the displayed state and original persisted version.
        // Read eligibility is advisory; no write lock or cancellation effects belong here.
        if (order.getStatus() == OrderStatus.NEW && order.getInventoryReleasedAt() == null
                && order.getCancelledAt() == null && order.getDeliveredAt() == null
                && order.getVersion() != null && order.getVersion() >= 0
                && currentUsers.currentUser().filter(user -> user.authorities().contains("ROLE_USER")).isPresent()
                && identities.findByUserId(buyer).filter(account -> account.active()
                        && account.roleCodes().contains("USER")).isPresent()) {
            cancellationVersion = order.getVersion();
        }
        return new BuyerOrderPageDetail(detail(order), cancellationVersion);
    }

    private Order ownedOrder(Long id, Long buyer) {
        if (id == null || id <= 0) throw notFound();
        // Never perform a global order lookup. Children are read only after this ownership check.
        return orders.findByIdAndBuyerId(id, buyer).orElseThrow(BuyerOrderService::notFound);
    }

    private BuyerOrderDetail detail(Order order) {
        var lines = items.findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(item -> new BuyerOrderDetail.Item(item.getProductId(), item.getProductNameSnapshot(),
                        item.getUnitPrice(), item.getDiscountSnapshot(), item.getFinalUnitPrice(),
                        item.getQuantity(), item.getLineTotal(), item.getId())).toList();
        var timeline = history.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                .map(entry -> new BuyerOrderDetail.TimelineEntry(entry.getFromStatus(), entry.getToStatus(),
                        entry.getCreatedAt())).toList();
        // Repeatable-read keeps status, history and payment facts from the same database snapshot.
        // Missing history/payment rows remain empty; createdAt/updatedAt never synthesize events.
        return new BuyerOrderDetail(order.getId(), order.getOrderCode(), order.getShopId(), order.getStatus(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getDeliveredAt(), order.getCancelledAt(),
                new BuyerOrderDetail.Address(order.getReceiverName(), order.getPhone(), order.getProvinceCode(),
                        order.getDistrict(), order.getDetail()), order.getSubtotal(), order.getDiscountTotal(),
                order.getShippingFee(), order.getGrandTotal(), lines, timeline, payments.recordsForOrder(order.getId()), order.getVoucher());
    }

    private Long buyerId() {
        return accounts.currentAccountId().filter(id -> id > 0)
                .orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED));
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
