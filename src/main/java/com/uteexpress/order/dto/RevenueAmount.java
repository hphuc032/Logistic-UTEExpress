package com.uteexpress.order.dto;

import java.math.BigDecimal;

/** A known zero is different from a total with unknown source events. */
public record RevenueAmount(BigDecimal value, long unknownEvents) {
    public RevenueAmount {
        if (unknownEvents < 0 || (unknownEvents == 0 && value == null)) throw new IllegalArgumentException();
        if (unknownEvents > 0) value = null;
    }
    @com.fasterxml.jackson.annotation.JsonProperty
    public boolean complete() { return unknownEvents == 0; }
    public RevenueAmount subtract(RevenueAmount other) {
        long unknown = unknownEvents + other.unknownEvents;
        return new RevenueAmount(unknown == 0 ? value.subtract(other.value) : null, unknown);
    }
}
