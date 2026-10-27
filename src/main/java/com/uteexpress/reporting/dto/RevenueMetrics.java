package com.uteexpress.reporting.dto;

public record RevenueMetrics(RevenueAmount deliveredSales, RevenueAmount collectedPayments,
        RevenueAmount refunds, RevenueAmount commissionAccrued, RevenueAmount commissionReversed,
        long totalOrders, long deliveredOrders, long cancelledOrders) {
    @com.fasterxml.jackson.annotation.JsonProperty
    public RevenueAmount netVendorSales() { return deliveredSales.subtract(refunds); }
    @com.fasterxml.jackson.annotation.JsonProperty
    public RevenueAmount netCommission() { return commissionAccrued.subtract(commissionReversed); }
    @com.fasterxml.jackson.annotation.JsonProperty
    public RevenueAmount netCollectedRevenue() { return collectedPayments.subtract(refunds); }
    @com.fasterxml.jackson.annotation.JsonProperty
    public RevenueAmount netRevenueAfterCommission() { return netCollectedRevenue().subtract(netCommission()); }
}
