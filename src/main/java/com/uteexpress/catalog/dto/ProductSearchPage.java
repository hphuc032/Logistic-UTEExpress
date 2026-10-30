package com.uteexpress.catalog.dto;

import java.util.List;

/** Bounded public discovery result and navigation metadata. */
public record ProductSearchPage(
        List<ProductCard> products,
        ProductSearchCriteria criteria,
        long totalItems,
        int page,
        int size,
        long totalPages,
        boolean hasPrevious,
        boolean hasNext) {

    public ProductSearchPage {
        products = List.copyOf(products);
    }

    public static ProductSearchPage empty(ProductSearchCriteria criteria) {
        return new ProductSearchPage(List.of(), criteria, 0, 0, ProductSearchCriteria.DEFAULT_SIZE,
                0, false, false);
    }
}
