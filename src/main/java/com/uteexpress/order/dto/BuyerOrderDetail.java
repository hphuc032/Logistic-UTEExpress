package com.uteexpress.order.dto;

import com.uteexpress.payment.dto.PaymentRecordView;
import com.uteexpress.promotion.dto.VoucherApplication;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Explicit buyer projection; excludes commission, checkout secrets, actors and internal reasons. */
public record BuyerOrderDetail(Long id, String orderCode, Long shopId, OrderStatus status,
        Instant createdAt, Instant updatedAt, Instant deliveredAt, Instant cancelledAt,
        Address address, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingFee,
        BigDecimal grandTotal, List<Item> items, List<TimelineEntry> timeline,
        List<PaymentRecordView> payments, VoucherApplication voucher) {
    public BuyerOrderDetail {
        items = List.copyOf(items);
        timeline = List.copyOf(timeline);
        payments = List.copyOf(payments);
    }

    public BuyerOrderDetail(Long id, String orderCode, Long shopId, OrderStatus status,
            Instant createdAt, Instant updatedAt, Instant deliveredAt, Instant cancelledAt,
            Address address, BigDecimal subtotal, BigDecimal discountTotal, BigDecimal shippingFee,
            BigDecimal grandTotal, List<Item> items, List<TimelineEntry> timeline, List<PaymentRecordView> payments) {
        this(id, orderCode, shopId, status, createdAt, updatedAt, deliveredAt, cancelledAt, address,
                subtotal, discountTotal, shippingFee, grandTotal, items, timeline, payments, null);
    }

    public record Address(String receiverName, String phone, String provinceCode,
            String district, String detail) { }

    public record Item(Long productId, String productNameSnapshot, BigDecimal unitPrice,
            BigDecimal discountSnapshot, BigDecimal finalUnitPrice, int quantity, BigDecimal lineTotal) { }

    /** Exactly one persisted history row; a null fromStatus denotes the recorded initial creation. */
    public record TimelineEntry(OrderStatus fromStatus, OrderStatus toStatus, Instant createdAt) { }
}
