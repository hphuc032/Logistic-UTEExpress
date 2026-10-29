package com.uteexpress.security;

import com.uteexpress.security.service.OtpRequestRateLimiter;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OtpRequestRateLimiterTest {
    @Test void independentPeersResetExactlyAtTheWindowBoundaryAndRetryAfterRoundsUp() {
        var clock=new TestClock();var limiter=limiter(clock,2,2);
        assertThat(limiter.acquire("192.0.2.1").allowed()).isTrue();
        assertThat(limiter.acquire("192.0.2.1").allowed()).isTrue();
        assertThat(limiter.acquire("192.0.2.1").retryAfterSeconds()).isEqualTo(60);
        assertThat(limiter.acquire("192.0.2.2").allowed()).isTrue();
        clock.now.set(Instant.EPOCH.plusMillis(59999));
        assertThat(limiter.acquire("192.0.2.1").retryAfterSeconds()).isEqualTo(1);
        clock.now.set(Instant.EPOCH.plusSeconds(60));
        assertThat(limiter.acquire("192.0.2.1").allowed()).isTrue();
    }
    @Test void saturatedCapacityFailsClosedWithoutForgettingAnActivePeer() {
        var clock=new TestClock();var limiter=limiter(clock,1,2);
        assertThat(limiter.acquire("a").allowed()).isTrue();assertThat(limiter.acquire("b").allowed()).isTrue();
        assertThat(limiter.acquire("new").allowed()).isFalse();assertThat(limiter.acquire("a").allowed()).isFalse();
        clock.now.set(Instant.EPOCH.plusSeconds(60));
        assertThat(limiter.acquire("new").allowed()).isTrue();
    }
    @Test void clockRollbackDoesNotRefreshAnExhaustedWindowAndMissingPeersShareOneBudget() {
        var clock=new TestClock();var limiter=limiter(clock,1,2);
        assertThat(limiter.acquire(null).allowed()).isTrue();
        assertThat(limiter.acquire("").allowed()).isFalse();
        assertThat(limiter.acquire("x".repeat(65)).allowed()).isFalse();
        clock.now.set(Instant.EPOCH.minusSeconds(60));
        assertThat(limiter.acquire(null).allowed()).isFalse();
    }
    @Test void concurrentRequestsCannotExceedTheConfiguredBudget() throws Exception {
        var clock=new TestClock();var limiter=limiter(clock,5,2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(12)) {
            var futures=new java.util.ArrayList<Future<Boolean>>();
            for(int i=0;i<30;i++) futures.add(pool.submit(()->{assertThat(start.await(5,TimeUnit.SECONDS)).isTrue();return limiter.acquire("peer").allowed();}));
            start.countDown();int allowed=0;
            for(var f:futures) if(f.get(5,TimeUnit.SECONDS)) allowed++;
            assertThat(allowed).isEqualTo(5);
        }
    }
    @Test void unsafeOrMissingConfigurationCannotDisableOrUnboundTheLimiter() {
        for(var window:List.of(Duration.ZERO,Duration.ofMillis(999),Duration.ofHours(2)))
            assertThatThrownBy(()->new OtpRequestLimitProperties(window,5,10)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new OtpRequestLimitProperties(null,5,10)).isInstanceOf(IllegalStateException.class);
        for(int requests:new int[]{0,-1,1001})
            assertThatThrownBy(()->new OtpRequestLimitProperties(Duration.ofMinutes(1),requests,10)).isInstanceOf(IllegalStateException.class);
        for(int clients:new int[]{0,-1,100001})
            assertThatThrownBy(()->new OtpRequestLimitProperties(Duration.ofMinutes(1),5,clients)).isInstanceOf(IllegalStateException.class);
    }
    private OtpRequestRateLimiter limiter(Clock clock,int requests,int clients){return new OtpRequestRateLimiter(new OtpRequestLimitProperties(Duration.ofMinutes(1),requests,clients),clock);}
    static class TestClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(Instant.EPOCH);
        @Override public Instant instant(){return now.get();}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
    }
}
