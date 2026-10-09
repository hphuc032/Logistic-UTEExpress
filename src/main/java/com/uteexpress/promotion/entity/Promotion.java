package com.uteexpress.promotion.entity;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "promotions", schema = "uteexpress")
public class Promotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "product_id", nullable = false, updatable = false) private Long productId;
    @Column(nullable = false, length = 200) private String name;
    @Column(name = "discount_percent", nullable = false, columnDefinition = "numeric") private BigDecimal discountPercent;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_by", nullable = false, updatable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected Promotion() { }

    public Promotion(Long productId, Long creator, String name, BigDecimal percent,
            Instant start, Instant end, boolean active, Instant now) {
        if (productId == null || productId <= 0 || creator == null || creator <= 0 || now == null) invalid();
        this.productId = productId;
        this.createdBy = creator;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        update(name, percent, start, end, active, now);
    }

    public void update(String name, BigDecimal percent, Instant start, Instant end, boolean active, Instant now) {
        if (name == null || name.strip().isEmpty() || name.strip().length() > 200
                || percent == null || percent.signum() <= 0 || percent.compareTo(new BigDecimal("100")) > 0
                || start == null || end == null || now == null) invalid();
        Instant normalizedStart = start.truncatedTo(ChronoUnit.MICROS);
        Instant normalizedEnd = end.truncatedTo(ChronoUnit.MICROS);
        if (!normalizedStart.isBefore(normalizedEnd)) invalid();
        this.name = name.strip();
        this.discountPercent = percent;
        this.startsAt = normalizedStart;
        this.endsAt = normalizedEnd;
        this.active = active;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void disable(Instant now) {
        if (active) { active = false; updatedAt = now.truncatedTo(ChronoUnit.MICROS); }
    }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getCreatedBy() { return createdBy; }
    public String getName() { return name; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    private static void invalid() { throw new ApplicationException(ErrorCode.VALIDATION_FAILED); }
}
