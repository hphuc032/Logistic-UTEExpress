package com.uteexpress.catalog.dto;

import java.util.List;

public record PublicShopView(String slug, String name, String description, List<ProductCard> products) {
    public PublicShopView {
        products = List.copyOf(products);
    }
}
