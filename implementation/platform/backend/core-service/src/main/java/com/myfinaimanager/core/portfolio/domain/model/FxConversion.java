package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A currency-conversion rate {@code from -> to}, as handed to the {@code portfolio} module by
 * {@code MarketDataGateway} (FD004). Multiply a {@code from} amount by {@link #rate()} to get the
 * {@code to} amount. Provider-neutral read model (AR-062, FR-026).
 *
 * @param rate       &gt; 0, exact as provided (decimal-safe — FR-008)
 * @param observedAt when the rate was observed / retrieved
 */
public record FxConversion(BigDecimal rate, Instant observedAt) {

    public FxConversion {
        Objects.requireNonNull(observedAt, "observedAt");
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be a positive amount");
        }
    }
}
