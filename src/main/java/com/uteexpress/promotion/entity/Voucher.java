package com.uteexpress.promotion.entity;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.common.money.Money;
import com.uteexpress.promotion.dto.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "vouchers")
public class Voucher {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64) private String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private VoucherScope scope;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private VoucherType type;
    @Column(name = "shop_id") private Long shopId;
    @Column(nullable = false, columnDefinition = "numeric") private BigDecimal value;
    @Column(name = "max_discount", precision = 19, scale = 2) private BigDecimal maxDiscount;
    @Column(name = "min_subtotal", nullable = false, precision = 19, scale = 2) private BigDecimal minSubtotal;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(name = "total_limit", nullable = false) private long totalLimit;
    @Column(name = "per_user_limit", nullable = false) private long perUserLimit;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected Voucher() { }

    public Voucher(String code, VoucherScope scope, Long shopId, VoucherType type, BigDecimal value,
            BigDecimal maxDiscount, BigDecimal minSubtotal, Instant startsAt, Instant endsAt,
            long totalLimit, long perUserLimit, boolean active, Long createdBy, Instant at) {
        this.code = VoucherCode.normalize(code);
        if (this.code.isEmpty() || scope == null || type == null || value == null || value.signum() <= 0
                || (scope == VoucherScope.SHOP ? shopId == null || shopId <= 0 : shopId != null)
                || startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)
                || totalLimit <= 0 || perUserLimit <= 0 || createdBy == null || createdBy <= 0 || at == null
                || (type == VoucherType.PERCENTAGE && value.compareTo(new BigDecimal("100")) > 0)) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        this.scope = scope;
        this.shopId = shopId;
        this.type = type;
        this.value = type == VoucherType.FIXED ? Money.requireAmount(value) : value;
        this.maxDiscount = maxDiscount == null ? null : Money.requireAmount(maxDiscount);
        this.minSubtotal = Money.requireAmount(minSubtotal);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.totalLimit = totalLimit;
        this.perUserLimit = perUserLimit;
        this.active = active;
        this.createdBy = createdBy;
        this.createdAt = at;
        this.updatedAt = at;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public long getTotalLimit() { return totalLimit; }
    public long getPerUserLimit() { return perUserLimit; }
    public VoucherScope getScope() { return scope; }
    public Long getShopId() { return shopId; }
    public VoucherType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public BigDecimal getMinSubtotal() { return minSubtotal; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }

    /** Caller holds the quota row lock. Released history also prevents redefining a used voucher. */
    public void update(String code, VoucherType type, BigDecimal value, BigDecimal maxDiscount,
            BigDecimal minSubtotal, Instant startsAt, Instant endsAt, long totalLimit,
            long perUserLimit, boolean active, boolean hasUsage, Instant at) {
        var candidate = new Voucher(code, scope, shopId, type, value, maxDiscount, minSubtotal,
                startsAt, endsAt, totalLimit, perUserLimit, active, createdBy, at);
        if (hasUsage && (!this.code.equals(candidate.code) || this.type != candidate.type
                || this.value.compareTo(candidate.value) != 0
                || !sameAmount(this.maxDiscount, candidate.maxDiscount)
                || this.minSubtotal.compareTo(candidate.minSubtotal) != 0
                || totalLimit < this.totalLimit || perUserLimit < this.perUserLimit)) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        this.code = candidate.code;
        this.type = candidate.type;
        this.value = candidate.value;
        this.maxDiscount = candidate.maxDiscount;
        this.minSubtotal = candidate.minSubtotal;
        this.startsAt = candidate.startsAt;
        this.endsAt = candidate.endsAt;
        this.totalLimit = candidate.totalLimit;
        this.perUserLimit = candidate.perUserLimit;
        this.active = active;
        this.updatedAt = at;
    }

    public void disable(Instant at) {
        if (!active) return;
        updatedAt = java.util.Objects.requireNonNull(at);
        active = false;
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    /** Inclusive start, exclusive end. PLATFORM applies to any otherwise eligible single-shop checkout. */
    public VoucherApplication apply(Long targetShop, BigDecimal subtotal, Instant now) {
        subtotal = Money.requireAmount(subtotal);
        if (targetShop == null || targetShop <= 0 || now == null) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        if (!active) throw new ApplicationException(ErrorCode.Detail.VOUCHER_INACTIVE);
        if (now.isBefore(startsAt)) throw new ApplicationException(ErrorCode.Detail.VOUCHER_FUTURE);
        if (!now.isBefore(endsAt)) throw new ApplicationException(ErrorCode.Detail.VOUCHER_EXPIRED);
        if (scope == VoucherScope.SHOP && !shopId.equals(targetShop)) throw new ApplicationException(ErrorCode.Detail.VOUCHER_WRONG_SHOP);
        if (subtotal.compareTo(minSubtotal) < 0) throw new ApplicationException(ErrorCode.Detail.VOUCHER_MINIMUM);
        BigDecimal raw = type == VoucherType.FIXED ? value : subtotal.multiply(value).movePointLeft(2);
        if (maxDiscount != null) raw = raw.min(maxDiscount);
        return new VoucherApplication(id, code, scope, Money.round(raw.min(subtotal)));
    }
}
