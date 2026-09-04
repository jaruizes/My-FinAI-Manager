package com.myfinaimanager.core.marketdata.domain.exceptions;

/** No usable instrument profile is available right now (transport failure or empty/malformed body). */
public final class InstrumentProfileUnavailableException extends MarketDataException {
    public InstrumentProfileUnavailableException(String message) { super(message); }
}
