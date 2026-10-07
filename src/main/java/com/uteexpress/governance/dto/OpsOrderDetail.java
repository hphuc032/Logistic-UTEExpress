package com.uteexpress.governance.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Internal operations view; no checkout key, request hash or payment reference. */
public record OpsOrderDetail(Long buyerId, Long version, Instant readyAt,
        Facts facts) {
    public record Facts(Long id, String orderCode, Long shopId, String status,
            Instant createdAt, Instant updatedAt, Instant deliveredAt, Instant cancelledAt,
            Address address, BigDecimal subtotal, BigDecimal discountTotal,
            BigDecimal shippingFee, Long shippingProviderId, String shippingServiceCode,
            BigDecimal grandTotal,
            List<Item> items, List<TimelineEntry> timeline, List<Payment> payments) {
        public Facts {
            items = List.copyOf(items);
            timeline = List.copyOf(timeline);
            payments = List.copyOf(payments);
        }
    }
    public record Address(String receiverName, String phone, String provinceCode,
            String district, String detail) { }
    public record Item(Long productId, String productNameSnapshot, BigDecimal unitPrice,
            BigDecimal discountSnapshot, BigDecimal finalUnitPrice, int quantity, BigDecimal lineTotal) { }
    public record TimelineEntry(String fromStatus, String toStatus, Instant createdAt) { }
    public record Payment(String method, String status, BigDecimal amount, Instant createdAt) { }
}
