package com.uteexpress.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

/** Public Product detail with safe image identifiers, never storage keys. */
public record ProductDetailView(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String shopSlug,
        String shopName,
        String categorySlug,
        String categoryName,
        List<ProductImageView> images) {

    public ProductDetailView {
        images = List.copyOf(images);
    }

    public ProductDetailView withImages(List<ProductImageView> productImages) {
        return new ProductDetailView(id, name, description, price, stock, shopSlug, shopName,
                categorySlug, categoryName, productImages);
    }
}
