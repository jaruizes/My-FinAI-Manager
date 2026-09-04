package com.myfinaimanager.core.marketdata.infrastructure.frankfurter;

import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;
import com.myfinaimanager.core.marketdata.domain.ports.FxRatePort;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.client.FrankfurterRestClient;
import com.myfinaimanager.core.marketdata.infrastructure.frankfurter.mapper.FrankfurterFxRateMapper;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Frankfurter implementation of {@link FxRatePort} (EN005 Revision 2). Frankfurter serves ECB
 * reference rates with <strong>no API key</strong> — it is independent of the Finnhub configuration
 * (VC-004). Each direction is fetched with {@code base=from}; the adapter returns rates only, no
 * conversion arithmetic (enabler §15). Wrapped by {@code CachingFxRatePort} ({@code @Primary}).
 *
 * <p>Selected by {@code market-data.fx.provider=frankfurter} ({@code matchIfMissing} — it is the
 * only FX provider today).
 */
@Component
@ConditionalOnProperty(name = "market-data.fx.provider", havingValue = "frankfurter", matchIfMissing = true)
public class FrankfurterFxRateAdapter implements FxRatePort {

    private final FrankfurterRestClient client;
    private final FrankfurterFxRateMapper mapper;
    private final Clock clock;

    public FrankfurterFxRateAdapter(FrankfurterRestClient client, FrankfurterFxRateMapper mapper,
                                    Clock clock) {
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public FxRate getRate(SupportedCurrency from, SupportedCurrency to) {
        if (from == to) {
            throw new IllegalArgumentException("from and to must differ");
        }
        return mapper.map(client.latest(from.name(), to.name()), from, to, clock.instant());
    }
}
