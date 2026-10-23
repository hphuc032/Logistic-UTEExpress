package com.uteexpress.payment.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.payment.dto.ManualRefundCommand;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ManualRefundPayloadTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test void matchesIndependentUtf8ProtocolVectorAndPreservesOriginalVersions() {
        // Reference vector calculated independently with Python struct big-endian framing + hashlib.
        var payload = payload(1L, 7L, 3L, " \u2003REF-đ\u2003 ", "key-1");
        assertThat(HEX.formatHex(payload.canonical())).isEqualTo(
                "000000010000001a50415930325f4d414e55414c5f434f445f524546554e445f5631"
                + "000000000000000100000000000000070000000000000003000000065245462dc491");
        assertThat(HEX.formatHex(payload.sha256()))
                .isEqualTo("528b0c14cee7cd0b632fde00ffeb1f23aac39b16e97f59cc93c2f2cbbf9c08b2");
        assertThat(payload.advisoryLockId()).isEqualTo(-5884642304351457655L);
        assertThat(payload.command()).isEqualTo(new ManualRefundCommand(1L, 7L, 3L, "REF-đ", "key-1"));
    }

    @Test void keyUsesApproved255CharacterLimitWithoutInventingAnAsciiGrammar() {
        for (String key : new String[]{"k".repeat(255), "đ".repeat(255), "😀".repeat(255), " a key:with / punctuation "}) {
            assertThat(payload(1L, 7L, 3L, "proof", key).command().idempotencyKey()).isEqualTo(key);
        }
        invalid(() -> payload(1L, 7L, 3L, "proof", "k".repeat(256)));
        invalid(() -> payload(1L, 7L, 3L, "proof", "😀".repeat(256)));
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\r\n", "\u2003", "\u0000", "bad\uD800", "bad\uDC00"})
    void rejectsMissingBlankNulOrMalformedKey(String key) {
        invalid(() -> payload(1L, 7L, 3L, "proof", key));
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "\t\r\n", "\u2003", "\u0000", "bad\uD800", "bad\uDC00"})
    void rejectsInvalidProof(String proof) {
        invalid(() -> payload(1L, 7L, 3L, proof, "key"));
    }

    @Test void proofLengthAppliesAfterStripAndProofCaseAndUnicodeAreSignificant() {
        assertThat(payload(1L, 7L, 3L, " " + "p".repeat(255) + " ", "key").command().proofReference())
                .hasSize(255);
        invalid(() -> payload(1L, 7L, 3L, "p".repeat(256), "key"));
        var upper = payload(1L, 7L, 3L, "REF", "key");
        assertThat(upper.canonical()).isNotEqualTo(payload(1L, 7L, 3L, "ref", "key").canonical());
        assertThat(payload(1L, 7L, 3L, "é", "key").canonical())
                .isNotEqualTo(payload(1L, 7L, 3L, "e\u0301", "key").canonical());
    }

    static Stream<ManualRefundCommand> invalidSelections() {
        return Stream.of(null,
                new ManualRefundCommand(null, 7L, 3L, "proof", "key"),
                new ManualRefundCommand(0L, 7L, 3L, "proof", "key"),
                new ManualRefundCommand(-1L, 7L, 3L, "proof", "key"),
                new ManualRefundCommand(1L, null, 3L, "proof", "key"),
                new ManualRefundCommand(1L, -1L, 3L, "proof", "key"),
                new ManualRefundCommand(1L, Long.MAX_VALUE, 3L, "proof", "key"),
                new ManualRefundCommand(1L, 7L, null, "proof", "key"),
                new ManualRefundCommand(1L, 7L, -1L, "proof", "key"));
    }

    @ParameterizedTest @MethodSource("invalidSelections")
    void rejectsInvalidSelectionsRatherThanReplacingExpectedVersions(ManualRefundCommand command) {
        invalid(() -> ManualRefundPayload.from(command));
    }

    @Test void zeroVersionsAndMaximumReturnVersionRemainOriginalTokens() {
        assertThat(payload(1L, 0L, Long.MAX_VALUE, "proof", "key").command())
                .isEqualTo(new ManualRefundCommand(1L, 0L, Long.MAX_VALUE, "proof", "key"));
    }

    @Test void keyAloneControlsLockWhileOrderAndOriginalVersionsControlPayload() {
        var original = payload(1L, 7L, 3L, "proof", "key");
        for (var changed : new ManualRefundPayload[]{payload(2L, 7L, 3L, "proof", "key"),
                payload(1L, 8L, 3L, "proof", "key"), payload(1L, 7L, 4L, "proof", "key"),
                payload(1L, 7L, 3L, "other", "key")}) {
            assertThat(changed.canonical()).isNotEqualTo(original.canonical());
            assertThat(changed.advisoryLockId()).isEqualTo(original.advisoryLockId());
        }
        var otherKey = payload(1L, 7L, 3L, "proof", " key ");
        assertThat(otherKey.canonical()).isEqualTo(original.canonical());
        assertThat(otherKey.advisoryLockId()).isNotEqualTo(original.advisoryLockId());
    }

    @Test void exactSameAdminReplayMatchesNormalizedOriginalRequestWithoutCurrentVersionInputs() {
        var original = payload(1L, 7L, 3L, "proof", "key");
        var retry = payload(1L, 7L, 3L, " proof ", "key");
        retry.requireReplayMatch(ManualRefundPayload.OPERATION, "key", original.canonical(), original.sha256(), 11L, 11L);
        // Authorization, committed visibility and response replay flag are the future coordinator's responsibility.
    }

    @ParameterizedTest @ValueSource(strings = {"operation", "key", "order", "orderVersion", "returnVersion", "proof", "admin", "missingAdmin", "missingActor", "zeroAdmin", "payload", "hash", "nullPayload", "nullHash"})
    void replayConflictsForEveryIdentityOrPayloadMismatchIncludingForgedHashMatch(String defect) {
        var original = payload(1L, 7L, 3L, "proof", "key");
        var retry = payload(defect.equals("order") ? 2L : 1L, defect.equals("orderVersion") ? 8L : 7L,
                defect.equals("returnVersion") ? 4L : 3L, defect.equals("proof") ? "other" : "proof", "key");
        byte[] canonical = original.canonical(), hash = original.sha256();
        if (defect.equals("payload")) canonical[0] ^= 1;
        if (defect.equals("hash")) hash[0] ^= 1;
        byte[] storedCanonical = defect.equals("nullPayload") ? null : canonical;
        byte[] storedHash = defect.equals("nullHash") ? null : hash;
        assertThatThrownBy(() -> retry.requireReplayMatch(defect.equals("operation") ? "OTHER" : ManualRefundPayload.OPERATION,
                defect.equals("key") ? "other-key" : "key", storedCanonical, storedHash,
                defect.equals("missingAdmin") ? null : defect.equals("zeroAdmin") ? 0L : 11L,
                defect.equals("missingActor") ? null : defect.equals("admin") ? 12L : 11L))
                .isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test void callersCannotMutateCanonicalOrHashThroughAccessors() {
        var payload = payload(1L, 7L, 3L, "proof", "key");
        byte[] canonical = payload.canonical(), hash = payload.sha256();
        payload.canonical()[0] ^= 1;
        payload.sha256()[0] ^= 1;
        assertThat(payload.canonical()).isEqualTo(canonical);
        assertThat(payload.sha256()).isEqualTo(hash);
    }

    private static ManualRefundPayload payload(Long order, Long ov, Long rv, String proof, String key) {
        return ManualRefundPayload.from(new ManualRefundCommand(order, ov, rv, proof, key));
    }
    private static void invalid(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }
}
