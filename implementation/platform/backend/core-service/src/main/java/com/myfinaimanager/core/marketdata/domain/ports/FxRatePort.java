package com.myfinaimanager.core.marketdata.domain.ports;

import com.myfinaimanager.core.marketdata.domain.exceptions.FxRateUnavailableException;
import com.myfinaimanager.core.marketdata.domain.exceptions.MarketDataNotConfiguredException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderAuthenticationFailedException;
import com.myfinaimanager.core.marketdata.domain.exceptions.ProviderRateLimitedException;
import com.myfinaimanager.core.marketdata.domain.model.FxRate;
import com.myfinaimanager.core.marketdata.domain.model.SupportedCurrency;

/**
 * Outbound port: obtain a currency-conversion rate, provider-neutral (EN005). Independently
 * replaceable (e.g. a future ECB adapter) without changing valuation business logic.
 *
 * <p>Contract: {@code specs/EN005-establish-finnhub-market-data-integration/contracts/market-data-ports.md} §C3.
 * The port supplies the rate only — no conversion arithmetic.
 */
public interface FxRatePort {

    /**
     * @param from source currency (must differ from {@code to})
     * @param to   target currency
     * @return an {@link FxRate} with a positive decimal rate and freshness metadata
     * @throws FxRateUnavailableException            no usable rate right now (incl. the pair being
     *                                               absent from the provider response)
     * @throws ProviderRateLimitedException          the provider throttled the call (HTTP 429)
     * @throws ProviderAuthenticationFailedException the provider rejected the credentials (401/403)
     * @throws MarketDataNotConfiguredException      no API key is configured in this environment
     * @throws IllegalArgumentException              {@code from == to}
     */
    FxRate getRate(SupportedCurrency from, SupportedCurrency to);
}
