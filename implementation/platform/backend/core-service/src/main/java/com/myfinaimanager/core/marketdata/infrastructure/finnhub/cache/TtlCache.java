package com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A minimal in-process, time-to-live cache (EN005 — plan.md OD-EN005-3). {@link #get} returns a
 * live non-expired entry or invokes {@code loader}, stores the result with an expiry of
 * {@code now + ttl}, and returns it. A {@code loader} exception propagates and <strong>nothing is
 * stored</strong> — failures are never cached (a transient error is retried on the next call).
 *
 * <p>No external cache technology, no dependency. Keys must have value-based {@code equals}/{@code hashCode}.
 */
public final class TtlCache<K, V> {

    private record Entry<V>(V value, Instant expiresAt) {
    }

    private final Clock clock;
    private final Duration ttl;
    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TtlCache(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public V get(K key, Supplier<V> loader) {
        Instant now = clock.instant();
        Entry<V> current = entries.get(key);
        if (current != null && now.isBefore(current.expiresAt())) {
            return current.value();
        }
        V loaded = loader.get(); // exception propagates; nothing stored
        entries.put(key, new Entry<>(loaded, now.plus(ttl)));
        return loaded;
    }
}
