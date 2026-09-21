package com.uteexpress.shipping.service;

import com.uteexpress.shipping.dto.ShippingQuote;
import com.uteexpress.shipping.dto.ShippingQuoteCommand;

/** QD-owned server quote boundary; validate active provider/service and supported destination. */
public interface ShippingQuoteService {
    ShippingQuote quote(ShippingQuoteCommand command);
}
