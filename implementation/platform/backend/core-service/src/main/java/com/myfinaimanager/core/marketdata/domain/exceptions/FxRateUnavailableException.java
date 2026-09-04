package com.myfinaimanager.core.marketdata.domain.exceptions;

/** No usable FX rate is available right now (transport failure or the requested currency pair is absent from the provider response). */
public final class FxRateUnavailableException extends MarketDataException {
    public FxRateUnavailableException(String message) { super(message); }
}
