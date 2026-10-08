package com.uteexpress.order.entity;

import com.uteexpress.checkout.dto.CheckoutQuote;
import com.uteexpress.checkout.dto.Money;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.OrderStatus;
import com.uteexpress.promotion.dto.VoucherApplication;
import com.uteexpress.promotion.dto.VoucherScope;
import java.util.Objects;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders", uniqueConstraints = {
    @UniqueConstraint(name = "uq_orders_order_code", columnNames = "order_code"),
    @UniqueConstraint(name = "uq_orders_buyer_id_checkout_key", columnNames = {"buyer_id", "checkout_key"})
}, indexes = {
    @Index(name = "ix_orders_buyer_id_created_at", columnList = "buyer_id,created_at"),
    @Index(name = "ix_orders_shop_id_status", columnList = "shop_id,status")
})
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_code", nullable = false)
    private String orderCode;
    @Column(name = "checkout_key", nullable = false)
    private String checkoutKey;
    @Column(name = "request_hash", nullable = false)
    private String requestHash;
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;
    @Column(name = "shop_id", nullable = false)
    private Long shopId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false)
    private OrderStatus status;
    @Column(name = "receiver_name", nullable = false)
    private String receiverName;
    @Column(name = "phone", nullable = false)
    private String phone;
    @Column(name = "province_code", nullable = false)
    private String provinceCode;
    @Column(name = "district", nullable = false)
    private String district;
    @Column(name = "detail", nullable = false)
    private String detail;
    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountTotal;
    // Nullable for legacy/no-voucher orders. No live relationship or historical backfill.
    @Column(name = "voucher_id", updatable = false) private Long voucherId;
    @Column(name = "voucher_code", length = 64, updatable = false) private String voucherCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_scope", length = 16, updatable = false) private VoucherScope voucherScope;
    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingFee;
    // Nullable only for legacy rows; historical checkout facts have no live provider relationship.
    @Column(name = "shipping_provider_id", updatable = false)
    private Long shippingProviderId;
    @Column(name = "shipping_service_code", length = 32, updatable = false)
    private String shippingServiceCode;
    @Column(name = "grand_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal grandTotal;
    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount;
    @Column(name = "commission_policy_id")
    private Long commissionPolicyId;
    @Column(name = "commission_rate_snapshot", nullable = false, columnDefinition = "numeric")
    private BigDecimal commissionRateSnapshot;
    @Column(name = "ready_at")
    private Instant readyAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "inventory_released_at")
    private Instant inventoryReleasedAt;
    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version @Column(name = "version", nullable = false)
    private Long version;
    protected Order() { }
    public Long getId() { return id; }
    public String getOrderCode() { return orderCode; }
    public String getCheckoutKey() { return checkoutKey; }
    public String getRequestHash() { return requestHash; }
    public Long getBuyerId() { return buyerId; }
    public Long getShopId() { return shopId; }
    public OrderStatus getStatus() { return status; }
    public String getReceiverName() { return receiverName; }
    public String getPhone() { return phone; }
    public String getProvinceCode() { return provinceCode; }
    public String getDistrict() { return district; }
    public String getDetail() { return detail; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDiscountTotal() { return discountTotal; }
    public VoucherApplication getVoucher() {
        return voucherId == null ? null : new VoucherApplication(voucherId, voucherCode, voucherScope, discountTotal);
    }
    public BigDecimal getShippingFee() { return shippingFee; }
    public Long getShippingProviderId() { return shippingProviderId; }
    public String getShippingServiceCode() { return shippingServiceCode; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public Long getCommissionPolicyId() { return commissionPolicyId; }
    public BigDecimal getCommissionRateSnapshot() { return commissionRateSnapshot; }
    public Instant getReadyAt() { return readyAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getInventoryReleasedAt() { return inventoryReleasedAt; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    /** Creates NEW from trusted domain snapshots; persistence belongs to the lifecycle service. */
    public Order(Long buyerId, String orderCode, String checkoutKey, String requestHash,
            CheckoutQuote quote, Instant at) {
        if (buyerId == null || buyerId <= 0 || quote == null || quote.shopId() == null || quote.shopId() <= 0
                || orderCode == null || orderCode.isBlank() || checkoutKey == null || checkoutKey.isBlank()
                || requestHash == null || requestHash.isBlank() || quote.items().isEmpty()
                || quote.shippingProviderId() == null || quote.shippingProviderId() <= 0
                || quote.shippingServiceSnapshot() == null
                // Same canonical service-code grammar as ShippingRateRequest and shipping_rates.
                || !quote.shippingServiceSnapshot().matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (var item : quote.items()) {
            sum = sum.add(OrderItem.validatedLineTotal(item));
        }
        if (sum.compareTo(quote.totals().subtotal()) != 0) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED);
        }
        this.buyerId = buyerId;
        this.shopId = quote.shopId();
        this.orderCode = orderCode;
        this.checkoutKey = checkoutKey;
        this.requestHash = requestHash;
        this.status = OrderStatus.NEW;
        this.receiverName = requireText(quote.address().receiverName());
        this.phone = requireText(quote.address().phone());
        this.provinceCode = requireText(quote.address().provinceCode());
        this.district = requireText(quote.address().district());
        this.detail = requireText(quote.address().detail());
        this.subtotal = quote.totals().subtotal();
        this.discountTotal = quote.totals().discountTotal();
        if (quote.voucher() != null) {
            if (quote.voucher().discountAmount().compareTo(discountTotal) != 0) {
                throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
            }
            this.voucherId = quote.voucher().voucherId();
            this.voucherCode = quote.voucher().code();
            this.voucherScope = quote.voucher().scope();
        }
        this.shippingFee = quote.totals().shippingFee();
        this.shippingProviderId = quote.shippingProviderId();
        this.shippingServiceCode = quote.shippingServiceSnapshot();
        this.grandTotal = quote.totals().grandTotal();
        this.commissionPolicyId = quote.commissionPolicyId();
        this.commissionRateSnapshot = Objects.requireNonNull(quote.commissionRateSnapshot());
        this.commissionAmount = Money.requireAmount(quote.commissionAmount());
        if (commissionRateSnapshot.signum() < 0 || commissionRateSnapshot.compareTo(new BigDecimal("100")) > 0
                || commissionAmount.compareTo(subtotal.subtract(discountTotal)) > 0
                || (commissionPolicyId != null && commissionPolicyId <= 0)) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED);
        }
        this.createdAt = Objects.requireNonNull(at);
        this.updatedAt = at;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED);
        }
        return value;
    }

    /**
     * Applies a transition already authorized and validated by the lifecycle service.
     * Internal domain operation, not a controller API; the transition policy stays in service.
     */
    public void markReady(Instant at) {
        if (status != OrderStatus.CONFIRMED || readyAt != null) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        readyAt = Objects.requireNonNull(at);
        updatedAt = at;
    }

    public void releaseInventory(Instant at) {
        if ((status != OrderStatus.NEW && status != OrderStatus.CONFIRMED) || inventoryReleasedAt != null) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        inventoryReleasedAt = Objects.requireNonNull(at);
    }

    public void applyValidatedTransition(OrderStatus target, Instant at, String reason) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(at);
        OrderStatus previous = status;
        status = target;
        updatedAt = at;
        if (target == OrderStatus.CANCELLED) {
            cancelledAt = at;
            cancellationReason = reason;
        }
        if (target == OrderStatus.DELIVERED && previous == OrderStatus.SHIPPING) {
            deliveredAt = at;
        }
    }
}
