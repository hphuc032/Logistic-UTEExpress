package com.uteexpress.notification;

import com.uteexpress.notification.realtime.SocketObservability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class SocketObservabilityTest {
    @Test void fixedCodeLogsNeverRenderExceptionSecretsAndCountersRetainUnsampledDenials(CapturedOutput output) {
        var observations = new SocketObservability();
        String secret = "raw-token-CSRF-private-payload-sentinel";
        for(int i=0;i<9;i++) observations.denied(SocketObservability.Reason.FRAME_DENIED,
                new org.springframework.dao.CannotAcquireLockException(secret,new java.sql.SQLException(secret,"55P03")));
        assertThat(observations.count(SocketObservability.Reason.FRAME_DENIED)).isEqualTo(9);
        assertThat(observations.count(SocketObservability.Reason.ACCOUNT_LOCK_UNAVAILABLE)).isEqualTo(9);
        assertThat(output.getAll()).contains("reason=FRAME_DENIED", "reason=ACCOUNT_LOCK_UNAVAILABLE", "observations=8")
                .doesNotContain(secret, "SQLException", "CannotAcquireLockException", "observations=9");
    }
}
