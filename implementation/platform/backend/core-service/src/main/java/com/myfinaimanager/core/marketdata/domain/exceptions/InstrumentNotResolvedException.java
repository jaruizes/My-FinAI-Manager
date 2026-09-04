package com.myfinaimanager.core.marketdata.domain.exceptions;

/** The instrument could not be resolved to a valid provider symbol; no provider call was made with a guessed symbol. */
public final class InstrumentNotResolvedException extends MarketDataException {
    public InstrumentNotResolvedException(String message) { super(message); }
}
