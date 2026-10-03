package com.uteexpress.order.service;

import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/** Versioned, length-prefixed canonical business request; never includes prices or checkoutKey. */
final class CheckoutRequestHash {
    private CheckoutRequestHash() { }

    static String calculate(CheckoutRequest request) {
        if (request == null || request.checkoutKey() == null || request.checkoutKey().isBlank()
                || request.checkoutKey().length() > 255 || request.items() == null || request.items().isEmpty()
                || request.addressId() == null || request.addressId() <= 0
                || request.shippingProviderId() == null || request.shippingProviderId() <= 0
                || request.shippingServiceCode() == null
                || !request.shippingServiceCode().matches("[A-Z][A-Z0-9_]{0,31}")
                || request.paymentMethod() == null
                || request.items().stream().anyMatch(item -> item == null || item.productId() == null
                        || item.productId() <= 0 || item.quantity() <= 0)
                || request.items().stream().map(CheckoutRequest.Item::productId).distinct().count() != request.items().size()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        }
        StringBuilder canonical = new StringBuilder("chk-02-v1;");
        field(canonical, request.items().size());
        request.items().stream().sorted(Comparator.comparing(CheckoutRequest.Item::productId)).forEach(item -> {
            field(canonical, item.productId());
            field(canonical, item.quantity());
        });
        field(canonical, request.addressId());
        field(canonical, request.shippingProviderId());
        field(canonical, request.shippingServiceCode());
        field(canonical, request.paymentMethod().name());
        field(canonical, request.voucherCode() == null ? "" : request.voucherCode().strip());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void field(StringBuilder target, Object value) {
        String text = value.toString();
        target.append(text.length()).append(':').append(text).append(';');
    }
}
