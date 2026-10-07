package com.uteexpress.promotion.entity;

import com.uteexpress.promotion.dto.VoucherApplication;
import java.util.Objects;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Retained redemption history. Pre-delivery cancellation releases quota, never the original facts. */
@Entity
@Table(name = "voucher_usages")
public class VoucherUsage {
    public enum Status { REDEEMED, RELEASED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "voucher_id", nullable = false, updatable = false) private Long voucherId;
    @Column(name = "user_id", nullable = false, updatable = false) private Long userId;
    @Column(name = "order_id", nullable = false, unique = true, updatable = false) private Long orderId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status;
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2, updatable = false) private BigDecimal discountAmount;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "released_at") private Instant releasedAt;
    @Version @Column(nullable = false) private Long version;
    protected VoucherUsage() { }

    public VoucherUsage(VoucherApplication application, Long buyer, Long orderId, Instant at) {
        this.voucherId = application.voucherId();
        this.userId = buyer;
        this.orderId = orderId;
        this.status = Status.REDEEMED;
        this.discountAmount = application.discountAmount();
        this.createdAt = at;
    }

    public Long getVoucherId() { return voucherId; }
    public Long getUserId() { return userId; }
    public BigDecimal getDiscountAmount() { return discountAmount; }

    /** Caller serializes with the Voucher and usage locks; repeated calls retain the first timestamp. */
    public void release(Instant at) {
        if (status == Status.RELEASED) return;
        releasedAt = Objects.requireNonNull(at);
        status = Status.RELEASED;
    }
}
