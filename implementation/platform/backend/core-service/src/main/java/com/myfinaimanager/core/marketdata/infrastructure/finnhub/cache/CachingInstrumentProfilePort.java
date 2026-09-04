package com.myfinaimanager.core.marketdata.infrastructure.finnhub.cache;

import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.ports.InstrumentProfilePort;
import com.myfinaimanager.core.marketdata.infrastructure.config.FinnhubProperties;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.FinnhubInstrumentProfileAdapter;
import java.time.Clock;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Caching decorator for {@link InstrumentProfilePort} (FR-023). Caches successful profiles per
 * {@link InstrumentIdentifier} for {@code finnhub.cache.profile-ttl} (long — a profile is not
 * time-critical); failures propagate uncached. {@code @Primary}.
 */
@Component
@Primary
public class CachingInstrumentProfilePort implements InstrumentProfilePort {

    private final InstrumentProfilePort delegate;
    private final TtlCache<InstrumentIdentifier, InstrumentProfile> cache;

    public CachingInstrumentProfilePort(FinnhubInstrumentProfileAdapter delegate,
                                        FinnhubProperties properties,
                                        Clock clock) {
        this.delegate = delegate;
        this.cache = new TtlCache<>(clock, properties.cache().profileTtl());
    }

    @Override
    public InstrumentProfile getProfile(InstrumentIdentifier instrument) {
        return cache.get(instrument, () -> delegate.getProfile(instrument));
    }
}
