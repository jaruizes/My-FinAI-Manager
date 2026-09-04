package com.myfinaimanager.core.marketdata.domain.ports;

import com.myfinaimanager.core.marketdata.domain.exceptions.InstrumentNotResolvedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataNotConfiguredException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderAuthenticationFailedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderRateLimitedException;
import com.myfinaimanager.core.marketdata.domain.model.InstrumentIdentifier;
import com.myfinaimanager.core.marketdata.domain.model.MarketPrice;

/**
 * Outbound port: obtain the latest available market price for a financial instrument, provider-
 * neutral (EN005). Independently replaceable — a future feature may swap the implementation without
 * touching {@link InstrumentProfilePort} or {@link FxRatePort}.
 *
 * <p>Contract: {@code specs/EN005-establish-finnhub-market-data-integration/contracts/market-data-ports.md} §C1.
 */
public interface MarketDataPort {

    /**
     * @return a {@link MarketPrice} with a positive decimal price, the instrument's currency, the
     *         data source, and freshness metadata
     * @throws MarketDataUnavailableException        no usable price right now (a zero/absent provider
     *                                               price is this, never a {@code MarketPrice} of 0)
     * @throws ProviderRateLimitedException          the provider throttled the call (HTTP 429)
     * @throws ProviderAuthenticationFailedException the provider rejected the credentials (401/403)
     * @throws InstrumentNotResolvedException        the instrument has no valid provider symbol
     * @throws MarketDataNotConfiguredException      no API key is configured in this environment
     */
    MarketPrice getLatestPrice(InstrumentIdentifier instrument);
}
