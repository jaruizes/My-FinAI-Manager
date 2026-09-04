package com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache;

import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.domain.ports.FxRatePort;
import com.myfinaimanager.core.marketdata.infrastructure.config.FrankfurterProperties;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.FrankfurterFxRateAdapter;
import java.time.Clock;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Caching decorator for {@link FxRatePort} (FR-031 / Q3 — kept). Caches successful rates per
 * directional pair for {@code frankfurter.cache.fx-ttl}; failures propagate uncached.
 * {@code @Primary} — every consumer gets the cache.
 *
 * <p>EN005 Revision 2: the delegate is now {@link FrankfurterFxRateAdapter} (ECB rates, keyless).
 */
@Component
@Primary
public class CachingFxRatePort implements FxRatePort {

    /** Directional cache key — {@code EUR->USD} is distinct from {@code USD->EUR}. */
    private record Pair(SupportedCurrency from, SupportedCurrency to) {
    }

    private final FxRatePort delegate;
    private final TtlCache<Pair, FxRate> cache;

    public CachingFxRatePort(FrankfurterFxRateAdapter delegate, FrankfurterProperties properties,
                             Clock clock) {
        this.delegate = delegate;
        this.cache = new TtlCache<>(clock, properties.cache().fxTtl());
    }

    @Override
    public FxRate getRate(SupportedCurrency from, SupportedCurrency to) {
        if (from == to) {
            throw new IllegalArgumentException("from and to must differ");
        }
        return cache.get(new Pair(from, to), () -> delegate.getRate(from, to));
    }
}
