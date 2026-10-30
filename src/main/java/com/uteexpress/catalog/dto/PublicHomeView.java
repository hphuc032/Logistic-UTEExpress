package com.uteexpress.catalog.dto;

import java.util.List;

public record PublicHomeView(
        List<ProductCard> products,
        List<PublicCategorySummary> categories,
        List<PublicShopSummary> shops) {
    public PublicHomeView {
        products = List.copyOf(products);
        categories = List.copyOf(categories);
        shops = List.copyOf(shops);
    }
}
