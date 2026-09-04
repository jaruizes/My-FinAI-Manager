package com.myfinaimanager.core.marketdata.domain.exceptions;

/** The market-data integration is not configured in this environment (no API key). Unrelated platform capabilities are unaffected. */
public final class MarketDataNotConfiguredException extends MarketDataException {
    public MarketDataNotConfiguredException(String message) { super(message); }
}
