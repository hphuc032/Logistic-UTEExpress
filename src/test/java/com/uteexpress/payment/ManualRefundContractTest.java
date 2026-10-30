package com.uteexpress.payment;

import com.uteexpress.payment.dto.ManualRefundRequest;
import com.uteexpress.payment.dto.RefundResult;
import com.uteexpress.payment.dto.RefundStatus;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ManualRefundContractTest {
    // Matches production application.yml's strict unknown-property policy. No HTTP endpoint is installed yet.
    private final JsonMapper mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test void approvedRequestNormalizesProofAndKeepsOriginalVersionTokens() {
        var input = mapper.readValue("""
                {"expectedOrderVersion":7,"expectedReturnRequestVersion":3,"proofReference":"  BANK-REF  "}
                """, ManualRefundRequest.class);
        assertThat(input).isEqualTo(new ManualRefundRequest(7L, 3L, "BANK-REF"));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(input)).isEmpty();
            assertThat(factory.getValidator().validate(new ManualRefundRequest(null, -1L, " "))).hasSize(3);
            assertThat(factory.getValidator().validate(new ManualRefundRequest(Long.MAX_VALUE, 0L, "proof")))
                    .hasSize(1);
            assertThat(factory.getValidator().validate(new ManualRefundRequest(0L, 0L, "p".repeat(256))))
                    .hasSize(1);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"refundAmount", "amount", "paymentId", "status", "actor", "processedBy", "orderId", "idempotencyKey"})
    void strictJsonRequestRejectsUnapprovedClientFacts(String field) {
        assertThatThrownBy(() -> mapper.readValue("""
                {"expectedOrderVersion":7,"expectedReturnRequestVersion":3,"proofReference":"proof","%s":1}
                """.formatted(field), ManualRefundRequest.class)).isInstanceOf(RuntimeException.class);
    }

    @Test void responseExposesOnlyApprovedFieldsAndReplayRetainsOriginalEvidence() {
        var time = Instant.parse("2026-10-21T00:00:00Z");
        var first = new RefundResult(1L, 2L, new BigDecimal("107000.00"), RefundStatus.SUCCEEDED, "proof", time, false);
        var replay = new RefundResult(1L, 2L, first.refundAmount(), first.status(), first.proofReference(), time, true);
        Map<?, ?> json = mapper.readValue(mapper.writeValueAsString(first), Map.class);
        assertThat(json.keySet().stream().map(Object::toString).toList()).containsExactlyInAnyOrder("refundId", "orderId", "refundAmount", "status",
                "proofReference", "serverTimestamp", "replay");
        assertThat(json.get("status")).isEqualTo("SUCCEEDED");
        assertThat(json.get("replay")).isEqualTo(false);
        Map<?, ?> replayJson = mapper.readValue(mapper.writeValueAsString(replay), Map.class);
        replayJson.remove("replay");
        json.remove("replay");
        assertThat(replayJson).isEqualTo(json);
        assertThat(RefundStatus.values()).containsExactly(RefundStatus.SUCCEEDED);
    }
}
