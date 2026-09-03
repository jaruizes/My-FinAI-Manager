package com.myfinaimanager.core.portfolio.domain.exceptions;

/**
 * A valid portfolio could not be persisted because of a transient infrastructure problem. Nothing
 * was persisted (FR-023). The caller may retry with the same idempotency key (FR-023a). Persistence
 * adapters translate infrastructure exceptions into this — framework exception types never cross
 * the port boundary (AR-002, AR-012).
 */
public final class PortfolioNotSavedException extends RuntimeException {

    public PortfolioNotSavedException(String message, Throwable cause) {
        super(message, cause);
    }
}
