package com.uteexpress.order.dto;

import com.uteexpress.payment.dto.PaymentRecordView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Explicit buyer projection; excludes commission, checkout secrets, actors and internal reasons. */
public record BuyerOrderDetail(Long id, String orderCode, Long shopId, OrderStatus status,
        Instant createdAt, Instant updatedAt, Instant deliveredAt, Instant cancelledAt,
        Address address, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingFee,
        BigDecimal grandTotal, List<Item> items, List<TimelineEntry> timeline,
        List<PaymentRecordView> payments) {
    public BuyerOrderDetail {
        items = List.copyOf(items);
        timeline = List.copyOf(timeline);
        payments = List.copyOf(payments);
    }

    public record Address(String receiverName, String phone, String provinceCode,
            String district, String detail) { }

    public record Item(Long productId, String productNameSnapshot, BigDecimal unitPrice,
            BigDecimal discountSnapshot, BigDecimal finalUnitPrice, int quantity, BigDecimal lineTotal) { }

    /** Exactly one persisted history row; a null fromStatus denotes the recorded initial creation. */
    public record TimelineEntry(OrderStatus fromStatus, OrderStatus toStatus, Instant createdAt) { }
}
