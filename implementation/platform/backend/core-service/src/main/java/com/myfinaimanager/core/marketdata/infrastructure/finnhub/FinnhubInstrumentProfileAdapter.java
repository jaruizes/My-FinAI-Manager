package com.myfinaimanager.core.marketdata.infrastructure.finnhub;

import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentProfile;
import com.myfinaimanager.core.marketdata.domain.ports.InstrumentProfilePort;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.client.FinnhubRestClient;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper.FinnhubProfileMapper;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver.FinnhubSymbolResolver;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Finnhub implementation of {@link InstrumentProfilePort} (EN005). Resolves the canonical instrument
 * to a Finnhub symbol (no call if it cannot be resolved), calls {@code /stock/profile2}, and maps
 * the response to a provider-neutral {@link InstrumentProfile}. Wrapped by
 * {@code CachingInstrumentProfilePort} ({@code @Primary}).
 */
@Component
public class FinnhubInstrumentProfileAdapter implements InstrumentProfilePort {

    private final FinnhubRestClient client;
    private final FinnhubSymbolResolver resolver;
    private final FinnhubProfileMapper mapper;
    private final Clock clock;

    public FinnhubInstrumentProfileAdapter(FinnhubRestClient client,
                                           FinnhubSymbolResolver resolver,
                                           FinnhubProfileMapper mapper,
                                           Clock clock) {
        this.client = client;
        this.resolver = resolver;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public InstrumentProfile getProfile(InstrumentIdentifier instrument) {
        String symbol = resolver.resolve(instrument);
        return mapper.map(client.profile(symbol), instrument, clock.instant());
    }
}
