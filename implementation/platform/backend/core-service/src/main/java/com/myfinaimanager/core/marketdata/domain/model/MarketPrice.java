package com.myfinaimanager.core.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * The latest available market price for a financial instrument, provider-neutral. A {@code MarketPrice}
 * is <strong>always</strong> a real, positive price — a missing or zero provider price is surfaced as
 * {@code MarketDataUnavailableException}, never as a {@code MarketPrice} with price {@code 0}
 * (FR-006). {@code price} is decimal-safe ({@link BigDecimal}); a deterministic valuation feature
 * consumes it as {@code positionValue = quantity * price}.
 *
 * @param instrument        the canonical identity the price is for
 * @param price             &gt; 0, exact as provided by the source
 * @param currency          the instrument's currency
 * @param observedAt        when the price was observed (provider timestamp) or retrieved
 * @param observedAtSource  which of the two {@code observedAt} represents (enabler §18)
 * @param source            the data source ({@link DataSource#FINNHUB})
 */
public record MarketPrice(
        InstrumentIdentifier instrument,
        BigDecimal price,
        SupportedCurrency currency,
        Instant observedAt,
        ObservedAtSource observedAtSource,
        DataSource source) {

    public MarketPrice {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(observedAtSource, "observedAtSource");
        Objects.requireNonNull(source, "source");
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be a positive amount");
        }
    }
}
