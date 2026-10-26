package com.uteexpress.order.dto;

import java.time.Instant;
import java.util.List;

public record RevenueSummary(String shopName, String timezone, Instant generatedAt,
        Window today, Window month, Window selected, TopProducts topProducts) {
    public record Window(RevenuePeriod period, RevenueMetrics metrics) { }
    public record Product(long productId, String productNameSnapshot, long deliveredQuantity,
            long receivedReturnQuantity, long netQuantity) { }
    public record TopProducts(List<Product> products, long unknownEvents) {
        public TopProducts { products = List.copyOf(products); }
        @com.fasterxml.jackson.annotation.JsonProperty
        public boolean complete() { return unknownEvents == 0; }
    }
}
