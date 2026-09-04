package com.myfinaimanager.core.marketdata.domain.exceptions;

/** No usable latest price is available right now (transport failure, empty/malformed body, or a non-positive provider price). */
public final class MarketDataUnavailableException extends MarketDataException {
    public MarketDataUnavailableException(String message) { super(message); }
}
