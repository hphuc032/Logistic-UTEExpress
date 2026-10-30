package com.uteexpress.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "products", schema = "uteexpress")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "moderation_reason", length = 1000)
    private String moderationReason;

    public String getModerationReason() { return moderationReason; }

    /** Only the versioned Ops service may invoke this transition. */
    public void moderate(boolean restrict, String reason, Instant now) {
        if (status != (restrict ? ProductStatus.ACTIVE : ProductStatus.MODERATED))
            throw new IllegalStateException("Invalid moderation transition");
        status = restrict ? ProductStatus.MODERATED : ProductStatus.ACTIVE;
        moderationReason = reason;
        updatedAt = now;
    }

    @Version
    @Column(nullable = false)
    private Long version;

    protected Product() {
    }

    public static Product create(Long shopId, Long categoryId, String name, String description,
            BigDecimal price, int stock, Instant now) {
        Product product = new Product();
        product.shopId = requirePositive(shopId, "shopId");
        product.createdAt = Objects.requireNonNull(now, "now");
        product.status = ProductStatus.ACTIVE;
        product.updateDetails(categoryId, name, description, price, stock, now);
        return product;
    }

    public void updateDetails(Long categoryId, String name, String description,
            BigDecimal price, int stock, Instant now) {
        this.categoryId = requirePositive(categoryId, "categoryId");
        this.name = requireText(name, "name");
        this.description = description;
        this.price = requireWholePositivePrice(price);
        if (stock < 0) throw new IllegalArgumentException("stock must be nonnegative");
        this.stock = stock;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    public void hide(Instant now) {
        status = ProductStatus.HIDDEN;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void decreaseStock(int quantity, Instant now) {
        if (quantity <= 0 || stock < quantity) throw new IllegalArgumentException("insufficient stock");
        stock -= quantity;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void restoreStock(int quantity, Instant now) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        stock = Math.addExact(stock, quantity);
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public Long getId() { return id; }
    public Long getShopId() { return shopId; }
    public Long getCategoryId() { return categoryId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public ProductStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static BigDecimal requireWholePositivePrice(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.remainder(BigDecimal.ONE).signum() != 0) {
            throw new IllegalArgumentException("price must be positive whole VND");
        }
        return value;
    }
}
