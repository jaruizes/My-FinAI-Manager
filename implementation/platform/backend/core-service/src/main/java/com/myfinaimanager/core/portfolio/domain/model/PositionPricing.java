package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A latest market price for one instrument, as handed to the {@code portfolio} module by
 * {@code MarketDataGateway} (FD004). Provider-neutral: this is the {@code portfolio} module's own
 * read model, deliberately not the {@code marketdata} module's {@code MarketPrice} (AR-062, FR-026).
 *
 * @param price      &gt; 0, exact as provided by the source (decimal-safe — FR-008)
 * @param observedAt when the price was observed / retrieved (freshness — §13 BR-013)
 */
public record PositionPricing(BigDecimal price, Instant observedAt) {

    public PositionPricing {
        Objects.requireNonNull(observedAt, "observedAt");
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be a positive amount");
        }
    }
}
