package com.uteexpress.order.dto;

import java.util.List;

public record VendorOrderPage(List<BuyerOrderSummary> content, int page, int size,
        long totalElements, int totalPages, boolean hasPrevious, boolean hasNext) {
    public VendorOrderPage { content = List.copyOf(content); }
}
