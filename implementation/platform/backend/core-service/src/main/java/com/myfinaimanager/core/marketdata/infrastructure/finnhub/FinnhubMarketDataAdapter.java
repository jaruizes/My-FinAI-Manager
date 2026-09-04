package com.myfinaimanager.core.marketdata.infrastructure.finnhub;

import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.ports.MarketDataPort;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.client.FinnhubRestClient;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper.FinnhubQuoteMapper;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.resolver.FinnhubSymbolResolver;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Finnhub implementation of {@link MarketDataPort} (EN005). Resolves the canonical instrument to a
 * Finnhub symbol (no call is made if it cannot be resolved), calls {@code /quote}, and maps the
 * response to a provider-neutral {@link MarketPrice}. The {@code CachingMarketDataPort} decorator
 * (which is {@code @Primary}) wraps this bean.
 *
 * <p>Selected by {@code market-data.price.provider=finnhub} ({@code matchIfMissing} — Finnhub is the
 * only price provider today; enabler §7, §16).
 */
@Component
@ConditionalOnProperty(name = "market-data.price.provider", havingValue = "finnhub", matchIfMissing = true)
public class FinnhubMarketDataAdapter implements MarketDataPort {

    private final FinnhubRestClient client;
    private final FinnhubSymbolResolver resolver;
    private final FinnhubQuoteMapper mapper;
    private final Clock clock;

    public FinnhubMarketDataAdapter(FinnhubRestClient client,
                                    FinnhubSymbolResolver resolver,
                                    FinnhubQuoteMapper mapper,
                                    Clock clock) {
        this.client = client;
        this.resolver = resolver;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public MarketPrice getLatestPrice(InstrumentIdentifier instrument) {
        String symbol = resolver.resolve(instrument); // InstrumentNotResolvedException — no outbound call
        Instant retrievalInstant = clock.instant();
        return mapper.map(client.quote(symbol), instrument, retrievalInstant);
    }
}
