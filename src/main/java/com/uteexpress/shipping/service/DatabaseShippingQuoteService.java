package com.uteexpress.shipping.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.shipping.dto.*;
import com.uteexpress.shipping.repository.ShippingRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal quote boundary: caller resolves/authorizes the shop and address, never client price. */
@Service
public class DatabaseShippingQuoteService implements ShippingQuoteService {
    private final ShippingRateRepository rates;
    public DatabaseShippingQuoteService(ShippingRateRepository rates) { this.rates=rates; }

    @Override @Transactional(readOnly=true)
    public ShippingQuote quote(ShippingQuoteCommand command) {
        if (command == null || command.shopId() == null || command.shopId() <= 0
                || command.providerId() == null || command.providerId() <= 0
                || !matches(command.serviceCode(), "[A-Z][A-Z0-9_]{0,31}")
                || !matches(command.provinceCode(), "[A-Z0-9][A-Z0-9_-]{0,19}")
                || !text(command.district(),120) || !text(command.detail(),255)) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        var rate = rates.findQuote(command.providerId(),command.serviceCode(),command.provinceCode())
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
        return new ShippingQuote(rate.getProvider().getId(), rate.getServiceCode(),
                rate.getFee().setScale(2), rate.getVersion());
    }
    private static boolean matches(String value,String pattern) { return value != null && value.matches(pattern); }
    private static boolean text(String value,int max) { return value != null && !value.isBlank() && value.length() <= max; }
}
