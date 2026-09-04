package com.myfinaimanager.core.marketdata.infrastructure.finnhub.mapper;

import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.domain.model.DataSource;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;
import com.myfinaimanager.core.marketdata.domain.model.ObservedAtSource;
import com.myfinaimanager.core.marketdata.infrastructure.finnhub.dto.FinnhubQuoteResponse;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Maps a Finnhub {@code /quote} response to a provider-neutral {@link MarketPrice}. A missing or
 * non-positive current price ({@code c}) is <strong>not</strong> a price — it raises
 * {@link MarketDataUnavailableException} (FR-006; enabler §8). The provider timestamp ({@code t},
 * unix seconds) becomes {@code observedAt} when present; otherwise the adapter's retrieval time is
 * used and marked as such (enabler §18). The result currency is the instrument's currency —
 * {@code /quote} does not return one.
 */
@Component
public class FinnhubQuoteMapper {

    public MarketPrice map(FinnhubQuoteResponse response,
                           InstrumentIdentifier instrument,
                           Instant retrievalInstant) {
        if (response.c() == null || response.c().signum() <= 0) {
            throw new MarketDataUnavailableException(
                    "provider returned no usable price for " + instrument.ticker());
        }
        boolean hasProviderTs = response.t() != null && response.t() > 0;
        Instant observedAt = hasProviderTs ? Instant.ofEpochSecond(response.t()) : retrievalInstant;
        ObservedAtSource observedAtSource = hasProviderTs
                ? ObservedAtSource.PROVIDER_TIMESTAMP
                : ObservedAtSource.RETRIEVAL_TIME;
        return new MarketPrice(instrument, response.c(), instrument.currency(), observedAt,
                observedAtSource, DataSource.FINNHUB);
    }
}
