package com.uteexpress.catalog.dto;

import java.math.BigDecimal;

/** Public Product summary. Internal ownership, status, version and storage data stay server-side. */
public record ProductCard(
        Long id,
        String name,
        BigDecimal price,
        int stock,
        String shopSlug,
        String shopName,
        String categorySlug,
        String categoryName,
        Long thumbnailImageId,
        BigDecimal discountSnapshot,
        BigDecimal effectivePrice) {
    public ProductCard(Long id, String name, BigDecimal price, int stock, String shopSlug, String shopName,
            String categorySlug, String categoryName, Long thumbnailImageId) {
        this(id, name, price, stock, shopSlug, shopName, categorySlug, categoryName, thumbnailImageId,
                BigDecimal.ZERO, price);
    }
}
