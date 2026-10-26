package com.uteexpress.notification.realtime;

import java.util.EnumMap;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Local counters and sampled fixed-code logs only: never accept request/session/header/payload text. */
@Component
public class SocketObservability {
    public enum Reason { HANDSHAKE_DENIED, FRAME_DENIED, DELIVERY_DENIED, SWEEP_DENIED, SESSION_LIMIT, SETUP_DENIED, CLEANUP_FAILED, ACCOUNT_LOCK_UNAVAILABLE }
    private final EnumMap<Reason, LongAdder> counts = new EnumMap<>(Reason.class);
    public SocketObservability() { for (var reason : Reason.values()) counts.put(reason, new LongAdder()); }
    public void rejected(Reason reason) {
        counts.get(reason).increment();
        long count = count(reason);
        // Powers-of-two sampling bounds repetitive denial logging; counters retain every observation.
        if ((count & (count - 1)) == 0)
            LoggerFactory.getLogger(SocketObservability.class).warn("ws_security reason={} observations={}", reason, count);
    }
    public void denied(Reason stage, RuntimeException failure) {
        rejected(stage);
        Throwable cause = failure;
        for (int depth=0; cause != null && depth<12; depth++, cause=cause.getCause()) {
            if (cause instanceof org.springframework.dao.PessimisticLockingFailureException
                    || cause instanceof org.springframework.dao.QueryTimeoutException
                    || cause instanceof org.springframework.transaction.TransactionTimedOutException
                    || cause instanceof java.sql.SQLException sql && ("55P03".equals(sql.getSQLState()) || "57014".equals(sql.getSQLState()))) {
                rejected(Reason.ACCOUNT_LOCK_UNAVAILABLE); return;
            }
        }
        // Never render the exception, cause, SQL, request or native headers.
    }
    public long count(Reason reason) { return counts.get(reason).sum(); }
}
