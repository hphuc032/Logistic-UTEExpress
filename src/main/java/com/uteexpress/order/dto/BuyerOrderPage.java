package com.uteexpress.order.dto;

import java.util.List;

public record BuyerOrderPage(List<BuyerOrderSummary> content, int page, int size,
        long totalElements, int totalPages, boolean hasPrevious, boolean hasNext) {
    public BuyerOrderPage { content = List.copyOf(content); }
}
