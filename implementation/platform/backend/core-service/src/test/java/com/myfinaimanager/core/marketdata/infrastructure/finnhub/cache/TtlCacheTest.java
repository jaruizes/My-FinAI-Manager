package com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TtlCacheTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-03T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    @Test
    void loads_once_and_serves_the_cached_value_within_the_ttl() {
        MutableClock clock = new MutableClock();
        TtlCache<String, String> cache = new TtlCache<>(clock, Duration.ofSeconds(45));
        AtomicInteger loads = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        }
        assertThat(loads).hasValue(1);
    }

    @Test
    void reloads_after_the_ttl_expires() {
        MutableClock clock = new MutableClock();
        TtlCache<String, String> cache = new TtlCache<>(clock, Duration.ofSeconds(45));
        AtomicInteger loads = new AtomicInteger();

        assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v1");
        clock.advance(Duration.ofSeconds(46));
        assertThat(cache.get("k", () -> "v" + loads.incrementAndGet())).isEqualTo("v2");
        assertThat(loads).hasValue(2);
    }

    @Test
    void a_loader_failure_is_not_cached_and_is_retried() {
        TtlCache<String, String> cache = new TtlCache<>(new MutableClock(), Duration.ofSeconds(45));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> cache.get("k", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cache.get("k", () -> {
            calls.incrementAndGet();
            return "ok";
        })).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }
}
