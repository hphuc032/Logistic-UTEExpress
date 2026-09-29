package com.uteexpress.catalog.dto;

import java.util.List;

public record PublicCategoryView(String slug, String name, List<ProductCard> products) {
    public PublicCategoryView {
        products = List.copyOf(products);
    }
}
