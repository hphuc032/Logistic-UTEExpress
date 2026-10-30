package com.uteexpress.payment.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.payment.dto.CancelledPaidRefundCommand;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import static com.uteexpress.payment.service.ManualRefundPayload.*;

/** Separate namespace/encoding; the existing returned-COD canonical bytes are unchanged. */
public final class CancelledPaidRefundPayload {
    public static final String OPERATION = "PAY02_CANCELLED_PAID_REFUND_V1";
    public static final int SCHEMA_VERSION = 1;
    private final CancelledPaidRefundCommand command;
    private final byte[] canonical;
    private final byte[] sha256;
    private final long advisoryLockId;
    private CancelledPaidRefundPayload(CancelledPaidRefundCommand command) {
        this.command = command;
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeInt(SCHEMA_VERSION); writeText(out, OPERATION);
            out.writeLong(command.orderId()); out.writeLong(command.expectedOrderVersion());
            writeText(out, command.proofReference()); canonical = bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        sha256 = digest(canonical);
        advisoryLockId = ByteBuffer.wrap(digest(encodeStrings(OPERATION, command.idempotencyKey()))).getLong();
    }
    public static CancelledPaidRefundPayload from(CancelledPaidRefundCommand c) {
        if (c == null || c.orderId() == null || c.orderId() <= 0 || c.expectedOrderVersion() == null
                || c.expectedOrderVersion() < 0 || c.expectedOrderVersion() == Long.MAX_VALUE)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        String key = requireText(c.idempotencyKey());
        String proof = requireText(c.proofReference() == null ? null : c.proofReference().strip());
        if (key.codePointCount(0, key.length()) > 255 || proof.length() > 255)
            throw new ApplicationException(ErrorCode.INVALID_REQUEST);
        return new CancelledPaidRefundPayload(new CancelledPaidRefundCommand(c.orderId(),c.expectedOrderVersion(),proof,key));
    }
    public CancelledPaidRefundCommand command() { return command; }
    public byte[] canonical() { return canonical.clone(); }
    public byte[] sha256() { return sha256.clone(); }
    public long advisoryLockId() { return advisoryLockId; }
    public void requireReplayMatch(String operation, String key, byte[] bytes, byte[] digest,
            Long originalAdmin, Long currentAdmin) {
        if (!OPERATION.equals(operation) || !command.idempotencyKey().equals(key)
                || originalAdmin == null || originalAdmin <= 0 || !Objects.equals(originalAdmin,currentAdmin)
                || !Arrays.equals(canonical,bytes) || !Arrays.equals(sha256,digest))
            throw new ApplicationException(ErrorCode.CONFLICT);
    }
}
