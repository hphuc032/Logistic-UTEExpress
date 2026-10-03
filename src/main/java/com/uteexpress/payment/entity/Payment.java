package com.uteexpress.payment.entity;

import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.checkout.dto.Money;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.payment.dto.PaymentStatus;
import java.util.Objects;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", uniqueConstraints = {
    @UniqueConstraint(name = "uq_payments_attempt_key", columnNames = "attempt_key"),
    @UniqueConstraint(name = "uq_payments_provider_reference", columnNames = "provider_reference")})
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Enumerated(EnumType.STRING) @Column(name = "method", nullable = false)
    private CheckoutRequest.PaymentMethod method;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false)
    private PaymentStatus status;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "provider_reference")
    private String providerReference;
    @Column(name = "attempt_key", nullable = false)
    private String attemptKey;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "expired_at")
    private Instant expiredAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected Payment() { }
    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public CheckoutRequest.PaymentMethod getMethod() { return method; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getProviderReference() { return providerReference; }
    public String getAttemptKey() { return attemptKey; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Amount comes from trusted order totals; no client status or amount is accepted. */
    public Payment(Long orderId, CheckoutRequest.PaymentMethod method,
            OrderTotals totals, String attemptKey, Instant at) {
        if (orderId == null || orderId <= 0 || attemptKey == null || attemptKey.isBlank()) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED);
        }
        this.orderId = orderId;
        this.method = Objects.requireNonNull(method);
        this.amount = Objects.requireNonNull(totals).grandTotal();
        this.attemptKey = attemptKey;
        this.status = PaymentStatus.UNPAID;
        this.createdAt = Objects.requireNonNull(at);
        this.updatedAt = at;
    }

    /** Internal transition. The application boundary must lock and reload authoritative order totals. */
    public void collectCod(BigDecimal authoritativeAmount, BigDecimal collectedAmount, Instant at) {
        BigDecimal due = Money.requireAmount(authoritativeAmount);
        BigDecimal collected = Money.requireAmount(collectedAmount);
        if (at == null) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        if (method != CheckoutRequest.PaymentMethod.COD || status != PaymentStatus.UNPAID
                || paidAt != null || expiredAt != null
                || Money.requireAmount(amount).compareTo(due) != 0 || collected.compareTo(due) != 0) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
        // Every validation precedes mutation, including for direct domain callers.
        status = PaymentStatus.PAID;
        paidAt = at;
        updatedAt = at;
    }

}
