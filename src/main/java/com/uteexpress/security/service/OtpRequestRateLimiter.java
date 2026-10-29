package com.uteexpress.security.service;

import com.uteexpress.security.OtpRequestLimitProperties;
import com.uteexpress.security.dto.RequestBudgetDecision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Bounded fixed windows per socket peer. Never evict an active budget to admit a new identity. */
@Service
public class OtpRequestRateLimiter {
    private final OtpRequestLimitProperties policy;
    private final Clock clock;
    private final Map<String,Window> clients=new HashMap<>();

    public OtpRequestRateLimiter(OtpRequestLimitProperties policy,Clock clock){this.policy=policy;this.clock=clock;}

    public synchronized RequestBudgetDecision acquire(String peer) {
        String key=peer==null || peer.isBlank() || peer.length()>64 ? "unknown-peer" : peer;
        Instant now=clock.instant();
        Window current=clients.get(key);
        if(current!=null && !now.isBefore(current.until)) {clients.remove(key);current=null;}
        if(current==null) {
            if(clients.size()>=policy.maxClients()) clients.values().removeIf(w -> !now.isBefore(w.until));
            if(clients.size()>=policy.maxClients()) {
                // Fail closed on saturation, without forgetting an existing attacker's active counter.
                Instant earliest=clients.values().stream().map(w->w.until).min(Instant::compareTo).orElseThrow();
                return denied(now,earliest);
            }
            current=new Window(now.plus(policy.window()));clients.put(key,current);
        }
        if(current.used>=policy.maxRequests()) return denied(now,current.until);
        current.used++;
        return new RequestBudgetDecision(true,0);
    }
    private static RequestBudgetDecision denied(Instant now,Instant until) {
        Duration remaining=Duration.between(now,until);
        return new RequestBudgetDecision(false,Math.max(1,remaining.getSeconds()+(remaining.getNano()>0?1:0)));
    }
    private static final class Window {
        final Instant until;
        int used;
        Window(Instant until){this.until=until;}
    }
}
