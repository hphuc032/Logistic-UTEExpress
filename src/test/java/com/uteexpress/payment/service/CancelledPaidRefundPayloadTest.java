package com.uteexpress.payment.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.payment.dto.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CancelledPaidRefundPayloadTest {
    private CancelledPaidRefundCommand command(String proof,String key) {
        return new CancelledPaidRefundCommand(7L,1L,proof,key);
    }
    @Test void operationSeparatesReturnedAndCancelledRefundCanonicalizationAndLockKeys() {
        var cancelled=CancelledPaidRefundPayload.from(command(" proof ","KEY"));
        var returned=ManualRefundPayload.from(new ManualRefundCommand(7L,1L,1L,"proof","KEY"));
        assertThat(cancelled.command().proofReference()).isEqualTo("proof");
        assertThat(cancelled.canonical()).isNotEqualTo(returned.canonical());
        assertThat(cancelled.advisoryLockId()).isNotEqualTo(returned.advisoryLockId());
        assertThat(CancelledPaidRefundPayload.from(command("proof","KEY")).canonical()).isEqualTo(cancelled.canonical());
    }
    @Test void exactKeyAndUnicodeLimitsArePreserved() {
        String key="\uD83D\uDE80".repeat(255);
        assertThat(CancelledPaidRefundPayload.from(command("proof",key)).command().idempotencyKey()).isEqualTo(key);
        assertThatThrownBy(()->CancelledPaidRefundPayload.from(command("proof",key+"x"))).isInstanceOf(ApplicationException.class);
        assertThat(CancelledPaidRefundPayload.from(command("proof"," KEY ")).advisoryLockId())
                .isNotEqualTo(CancelledPaidRefundPayload.from(command("proof","KEY")).advisoryLockId());
    }
    @Test void replayRequiresFullCanonicalBytesAndOriginalAdmin() {
        var p=CancelledPaidRefundPayload.from(command("proof","KEY"));
        p.requireReplayMatch(p.OPERATION,"KEY",p.canonical(),p.sha256(),9L,9L);
        assertThatThrownBy(()->p.requireReplayMatch(p.OPERATION,"KEY",p.canonical(),p.sha256(),9L,10L)).isInstanceOf(ApplicationException.class);
        var bytes=p.canonical();bytes[bytes.length-1]^=1;
        assertThatThrownBy(()->p.requireReplayMatch(p.OPERATION,"KEY",bytes,p.sha256(),9L,9L)).isInstanceOf(ApplicationException.class);
        assertThat(p.canonical()).isNotEqualTo(bytes);
    }
    @Test void invalidTextAndVersionsFailClosed() {
        for(String proof:new String[]{null," ","\0","\uD800","p".repeat(256)})
            assertThatThrownBy(()->CancelledPaidRefundPayload.from(command(proof,"KEY"))).isInstanceOf(ApplicationException.class);
        for(Long version:new Long[]{null,-1L,Long.MAX_VALUE})
            assertThatThrownBy(()->CancelledPaidRefundPayload.from(new CancelledPaidRefundCommand(7L,version,"proof","KEY"))).isInstanceOf(ApplicationException.class);
    }
}
