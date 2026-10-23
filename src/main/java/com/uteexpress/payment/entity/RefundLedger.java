package com.uteexpress.payment.entity;

import com.uteexpress.payment.dto.RefundResult;
import com.uteexpress.payment.dto.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** Immutable persisted evidence; no update or deletion API. */
public record RefundLedger(long id, long orderId, long processedBy, String operation, String key,
        byte[] canonical, byte[] sha256, BigDecimal amount, String proof, Instant timestamp) {
    public RefundLedger {
        canonical = canonical.clone();
        sha256 = sha256.clone();
    }
    @Override public byte[] canonical() { return canonical.clone(); }
    @Override public byte[] sha256() { return sha256.clone(); }
    public RefundResult result(boolean replay) {
        return new RefundResult(id, orderId, amount, RefundStatus.SUCCEEDED, proof, timestamp, replay);
    }
}
