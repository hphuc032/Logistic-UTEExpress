package com.uteexpress.reporting.dto;

/** Trusted wrapper-issued scope; never bind this DTO from HTTP input. */
public record RevenueScope(Kind kind, Long shopId) {
    public enum Kind { ALL_SHOPS, SHOP }
    public RevenueScope {
        if (kind == null || kind == Kind.SHOP && (shopId == null || shopId <= 0)
                || kind == Kind.ALL_SHOPS && shopId != null) throw new IllegalArgumentException("Invalid reporting scope");
    }
    public static RevenueScope allShops() { return new RevenueScope(Kind.ALL_SHOPS, null); }
    public static RevenueScope shop(long id) { return new RevenueScope(Kind.SHOP, id); }
}
