package com.uteexpress.payment;

import com.uteexpress.payment.config.VnpayProperties;
import com.uteexpress.payment.service.VnpayGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class VnpayGatewayTest {
    private final VnpayProperties config = new VnpayProperties("TESTCODE", "unit-test-secret",
            "https://uteexpress-web.onrender.com/payments/vnpay/return");
    private final VnpayGateway gateway = new VnpayGateway(config);

    @Test void matchesIndependentPythonHmacSha512VectorAndIgnoresHashMetadata() {
        var fields = new HashMap<>(Map.of("vnp_OrderInfo", "Thanh toan don hang 1",
                "vnp_TmnCode", "TESTCODE", "vnp_Amount", "10700000"));
        // Independently calculated with Python hmac/hashlib, using form-encoded sorted fields.
        String expected = "96f8cd05346a49d46237f95bbd40a4ac37e4e5966bbc90963c241eecfef9e765fa"
                + "78132ede9e284afa44e48e9434331c5258f426fdffcb9719fdd12612e43082";
        assertThat(gateway.signature(fields)).isEqualTo(expected);
        fields.put("vnp_SecureHash", expected.toUpperCase());
        fields.put("vnp_SecureHashType", "HmacSHA512");
        fields.put("untrusted", "ignored");
        assertThat(gateway.validSignature(fields)).isTrue();
        fields.put("vnp_Amount", "100");
        assertThat(gateway.validSignature(fields)).isFalse();
    }

    @Test void buildsSandboxOnlyUrlWithDongTimes100AndVietnamDates() {
        String url = gateway.paymentUrl("VP0123456789abcdef0123456789abcdef", 1L, new BigDecimal("107000.00"),
                Instant.parse("2026-10-30T00:00:00Z"), "127.0.0.1");
        assertThat(url).startsWith(VnpayGateway.SANDBOX_URL + "?")
                .contains("vnp_Amount=10700000", "vnp_CreateDate=20261030070000", "vnp_ExpireDate=20261030071500",
                        "vnp_CurrCode=VND", "vnp_ReturnUrl=https%3A%2F%2Futeexpress-web.onrender.com%2Fpayments%2Fvnpay%2Freturn")
                .matches(".*vnp_SecureHash=[0-9a-f]{128}$");
        assertThat(config.toString()).doesNotContain("unit-test-secret", "TESTCODE");
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "107000.01", "10000000000"})
    void rejectsUnrepresentableAmounts(String amount) {
        assertThatThrownBy(() -> VnpayGateway.encodedAmount(new BigDecimal(amount))).isInstanceOf(RuntimeException.class);
    }

    @Test void absentOrMalformedSignatureIsRejected() {
        assertThat(gateway.validSignature(Map.of())).isFalse();
        assertThat(gateway.validSignature(Map.of("vnp_SecureHash", "not-hex"))).isFalse();
    }

    @Test void validatesConfigurationWithoutPrintingCredentials() {
        assertThatThrownBy(() -> new VnpayProperties("", "", config.returnUrl())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VnpayProperties("TESTCODE", "unit-test-secret", "https://example.org/other"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
