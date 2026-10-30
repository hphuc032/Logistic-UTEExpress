package com.uteexpress.catalog.dto;

/** Public discovery sort allowlist. Client text is mapped to one of these values before SQL is built. */
public enum ProductSort {
    NEWEST("newest"),
    PRICE_ASC("priceAsc"),
    PRICE_DESC("priceDesc"),
    BEST_SELLING("bestSelling");

    private final String parameter;

    ProductSort(String parameter) {
        this.parameter = parameter;
    }

    public String parameter() {
        return parameter;
    }

    public static ProductSort fromParameter(String value) {
        if (value == null) {
            return NEWEST;
        }
        for (ProductSort sort : values()) {
            if (sort.parameter.equals(value)) {
                return sort;
            }
        }
        return NEWEST;
    }
}
