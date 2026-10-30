package com.uteexpress.shipping.service;

import com.uteexpress.shipping.dto.ShippingQuote;
import com.uteexpress.shipping.dto.ShippingQuoteCommand;

/** QD-owned server quote boundary; validate active provider/service and supported destination. */
public interface ShippingQuoteService {
    /** Active choices only; the caller supplies an authorized server-resolved destination. */
    java.util.List<com.uteexpress.shipping.dto.ShippingRateView> availableServices(String provinceCode);

    ShippingQuote quote(ShippingQuoteCommand command);
}
