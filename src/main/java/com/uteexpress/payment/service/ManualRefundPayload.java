package com.uteexpress.payment.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.payment.dto.ManualRefundCommand;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Pure canonicalization/matching, not authorization or a persistence boundary.
 * Wire encoding: big-endian int schema; length-prefixed UTF-8 operation; long Order ID,
 * original Order version, original ReturnRequest version; length-prefixed UTF-8 stripped proof.
 * Key is not trimmed and is outside the payload; it scopes the database/advisory lookup.
 */
public final class ManualRefundPayload {
    public static final int SCHEMA_VERSION = 1;
    public static final String OPERATION = "PAY02_MANUAL_COD_REFUND_V1";
    private final ManualRefundCommand command;
    private final byte[] canonical;
    private final byte[] sha256;
    private final long advisoryLockId;

    private ManualRefundPayload(ManualRefundCommand command) {
        this.command = command;
        this.canonical = encodePayload(command);
        this.sha256 = digest(canonical);
        // Same length-prefix encoding binds the lock identifier to operation AND exact key.
        this.advisoryLockId = ByteBuffer.wrap(digest(encodeStrings(OPERATION, command.idempotencyKey())))
                .getLong();
    }

    public static ManualRefundPayload from(ManualRefundCommand command) {
        if (command == null || command.orderId() == null || command.orderId() <= 0
                || command.expectedOrderVersion() == null || command.expectedOrderVersion() < 0
                || command.expectedOrderVersion() == Long.MAX_VALUE
                || command.expectedReturnRequestVersion() == null || command.expectedReturnRequestVersion() < 0) {
            throw invalid();
        }
        String key = requireText(command.idempotencyKey());
        String proof = requireText(command.proofReference() == null ? null : command.proofReference().strip());
        if (key.codePointCount(0, key.length()) > 255 || proof.length() > 255) throw invalid();
        return new ManualRefundPayload(new ManualRefundCommand(command.orderId(), command.expectedOrderVersion(),
                command.expectedReturnRequestVersion(), proof, key));
    }

    public ManualRefundCommand command() { return command; }
    public byte[] canonical() { return canonical.clone(); }
    public byte[] sha256() { return sha256.clone(); }
    public long advisoryLockId() { return advisoryLockId; }

    /**
     * Compare only COMMITTED evidence, after separately authorizing the current persisted ADMIN.
     * This helper neither authorizes the supplied actor nor checks current lifecycle versions.
     * Compare full bytes as well as the digest; a hash match alone cannot authorize a replay.
     */
    public void requireReplayMatch(String operation, String key, byte[] storedCanonical, byte[] storedSha256,
            Long originalAdminId, Long currentlyAuthorizedAdminId) {
        if (!OPERATION.equals(operation) || !command.idempotencyKey().equals(key)
                || originalAdminId == null || originalAdminId <= 0 || currentlyAuthorizedAdminId == null
                || !Objects.equals(originalAdminId, currentlyAuthorizedAdminId)
                || !Arrays.equals(canonical, storedCanonical) || !Arrays.equals(sha256, storedSha256)) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }

    static String requireText(String value) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) throw invalid();
        utf8(value); // Reject malformed UTF-16 rather than silently replacing it during hashing.
        return value;
    }

    private static byte[] encodePayload(ManualRefundCommand command) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeInt(SCHEMA_VERSION);
            writeText(out, OPERATION);
            out.writeLong(command.orderId());
            out.writeLong(command.expectedOrderVersion());
            out.writeLong(command.expectedReturnRequestVersion());
            writeText(out, command.proofReference());
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot encode refund payload", impossible);
        }
    }

    static byte[] encodeStrings(String... values) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            for (String value : values) writeText(out, value);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Cannot encode refund lock key", impossible);
        }
    }

    static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] encoded = utf8(value);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static byte[] utf8(String value) {
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException invalidUnicode) {
            throw invalid();
        }
    }

    static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static ApplicationException invalid() { return new ApplicationException(ErrorCode.INVALID_REQUEST); }
}
