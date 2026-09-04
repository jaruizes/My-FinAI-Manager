package com.myfinaimanager.core.marketdata.domain.exceptions;

/**
 * Base type for every market-data failure. The {@code message} is always key-free and safe to log.
 * No cause is carried: the adapter deliberately does not chain the provider exception (whose message
 * can contain provider detail) into the neutral failure — it logs a status category instead
 * (research D5, D9).
 */
public abstract class MarketDataException extends RuntimeException {

    protected MarketDataException(String message) {
        super(message);
    }
}
