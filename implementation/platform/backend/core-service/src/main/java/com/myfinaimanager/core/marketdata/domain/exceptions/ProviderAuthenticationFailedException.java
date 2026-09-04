package com.myfinaimanager.core.marketdata.domain.exceptions;

/** The market-data provider rejected the credentials (HTTP 401/403). The API key value is never included in this message. */
public final class ProviderAuthenticationFailedException extends MarketDataException {
    public ProviderAuthenticationFailedException(String message) { super(message); }
}
