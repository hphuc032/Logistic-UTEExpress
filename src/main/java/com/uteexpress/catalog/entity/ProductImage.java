package com.uteexpress.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "product_images", schema = "uteexpress")
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(nullable = false)
    private int position;

    @Column(name = "alt_text", length = 255)
    private String altText;

    protected ProductImage() {
    }

    public static ProductImage create(Long productId, String storageKey, int position, String altText) {
        if (productId == null || productId <= 0) throw new IllegalArgumentException("productId must be positive");
        if (position < 0) throw new IllegalArgumentException("position must be nonnegative");
        ProductImage image = new ProductImage();
        image.productId = productId;
        image.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        image.position = position;
        image.altText = altText;
        return image;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getStorageKey() { return storageKey; }
    public int getPosition() { return position; }
    public String getAltText() { return altText; }
}
