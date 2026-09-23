package com.uteexpress.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "shops", schema = "uteexpress")
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, unique = true)
    private Long ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "logo_key", length = 512)
    private String logoKey;

    @Column(name = "banner_key", length = 512)
    private String bannerKey;

    @Column(length = 2000)
    private String description;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShopStatus status;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Shop() {
    }

    public static Shop pendingApplication(Long ownerId, String name, String slug,
            String description, String pickupAddress, Instant now) {
        Shop shop = new Shop();
        shop.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        shop.name = Objects.requireNonNull(name, "name");
        shop.slug = Objects.requireNonNull(slug, "slug");
        shop.logoKey = null;
        shop.bannerKey = null;
        shop.description = description;
        shop.pickupAddress = Objects.requireNonNull(pickupAddress, "pickupAddress");
        shop.status = ShopStatus.PENDING;
        shop.rejectionReason = null;
        shop.createdAt = Objects.requireNonNull(now, "now");
        shop.updatedAt = now;
        return shop;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getLogoKey() { return logoKey; }
    public String getBannerKey() { return bannerKey; }
    public String getDescription() { return description; }
    public String getPickupAddress() { return pickupAddress; }
    public ShopStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
