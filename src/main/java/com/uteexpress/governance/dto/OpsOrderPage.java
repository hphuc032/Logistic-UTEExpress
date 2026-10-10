package com.uteexpress.governance.dto;

import java.util.List;

public record OpsOrderPage(List<OpsOrderSummary> content, int page, int size,
        long totalElements, boolean hasPrevious, boolean hasNext) {
    public OpsOrderPage { content = List.copyOf(content); }
}
