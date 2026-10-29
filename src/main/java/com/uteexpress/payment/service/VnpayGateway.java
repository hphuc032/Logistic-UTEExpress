package com.uteexpress.payment.service;

import com.uteexpress.payment.config.VnpayProperties;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** VNPAY PAY 2.1.0. Sandbox host is deliberately fixed; no production gateway configuration. */
@Component
@ConditionalOnProperty(name = "uteexpress.payment.vnpay.enabled", havingValue = "true")
public class VnpayGateway {
    public static final String SANDBOX_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    public static final java.time.Duration WINDOW = java.time.Duration.ofMinutes(15);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final VnpayProperties properties;

    public VnpayGateway(VnpayProperties properties) { this.properties = properties; }

    public String paymentUrl(String reference, Long orderId, BigDecimal amount, Instant createdAt, String ip) {
        var fields = new TreeMap<String, String>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.tmnCode());
        fields.put("vnp_Amount", encodedAmount(amount));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", reference);
        fields.put("vnp_OrderInfo", "Thanh toan don hang " + orderId);
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.returnUrl());
        fields.put("vnp_IpAddr", ip);
        fields.put("vnp_CreateDate", DATE.format(createdAt));
        fields.put("vnp_ExpireDate", DATE.format(createdAt.plus(WINDOW)));
        return SANDBOX_URL + "?" + canonical(fields) + "&vnp_SecureHash=" + signature(fields);
    }

    public boolean validSignature(Map<String, String> fields) {
        String supplied = fields.get("vnp_SecureHash");
        if (supplied == null || !supplied.matches("[0-9a-fA-F]{128}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(supplied), HexFormat.of().parseHex(signature(fields)));
    }

    public boolean validMerchant(Map<String, String> fields) {
        return properties.tmnCode().equals(fields.get("vnp_TmnCode"));
    }

    public static String encodedAmount(BigDecimal amount) {
        // VND uses whole dong; no floating point or silent rounding before multiplying by 100.
        String value = amount.setScale(0, java.math.RoundingMode.UNNECESSARY).movePointRight(2).toPlainString();
        if (amount.signum() <= 0 || value.length() > 12) throw new IllegalArgumentException("Invalid VNPAY amount");
        return value;
    }

    public String signature(Map<String, String> fields) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(canonical(fields).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA512 unavailable");
        }
    }

    private static String canonical(Map<String, String> fields) {
        return new TreeMap<>(fields).entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("vnp_")
                        && !entry.getKey().equals("vnp_SecureHash") && !entry.getKey().equals("vnp_SecureHashType")
                        && entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
