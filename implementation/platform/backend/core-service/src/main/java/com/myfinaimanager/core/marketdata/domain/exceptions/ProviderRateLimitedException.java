package com.myfinaimanager.core.marketdata.domain.exceptions;

/** The market-data provider rejected the call for exceeding its rate limit (HTTP 429). No data was fabricated. */
public final class ProviderRateLimitedException extends MarketDataException {
    public ProviderRateLimitedException(String message) { super(message); }
}
