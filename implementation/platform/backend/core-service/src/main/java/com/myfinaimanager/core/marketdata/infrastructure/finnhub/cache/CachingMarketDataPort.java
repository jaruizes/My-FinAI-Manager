package com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache;

import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.ports.MarketDataPort;
import com.myfinaimanager.core.marketdata.infrastructure.config.FinnhubProperties;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubMarketDataAdapter;
import java.time.Clock;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Short-lived in-process caching decorator for {@link MarketDataPort} (FR-023; enabler §19). Caches
 * <strong>successful</strong> {@link MarketPrice} results per {@link InstrumentIdentifier} for
 * {@code finnhub.cache.quote-ttl}; failures propagate and are never cached. A cached result is
 * returned unmodified — its {@code observedAt} is not rewritten, so callers still see the true age.
 * {@code @Primary} — this is the bean {@code MarketDataPort} consumers receive.
 */
@Component
@Primary
public class CachingMarketDataPort implements MarketDataPort {

    private final MarketDataPort delegate;
    private final TtlCache<InstrumentIdentifier, MarketPrice> cache;

    public CachingMarketDataPort(FinnhubMarketDataAdapter delegate,
                                 FinnhubProperties properties,
                                 Clock clock) {
        this.delegate = delegate;
        this.cache = new TtlCache<>(clock, properties.cache().quoteTtl());
    }

    @Override
    public MarketPrice getLatestPrice(InstrumentIdentifier instrument) {
        return cache.get(instrument, () -> delegate.getLatestPrice(instrument));
    }
}
