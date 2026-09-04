package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The valuation of one Position inside a {@link PortfolioValuation} snapshot (FD004 §7).
 *
 * <p>A Position that could not be priced is <strong>unvalued</strong>: {@link #valued()} is
 * {@code false} and every monetary field is {@link Optional#empty()} — <em>never</em> {@code 0}
 * (FR-017, FR-019). The {@link #sector()} string is always set (the provider classification, or the
 * literal {@code "Unclassified"} — FR-011, FR-014).
 *
 * <p>All amounts are {@link BigDecimal} (FR-008). {@link #portfolioWeight()} is an exact fraction
 * of the Portfolio's total EUR value (scale 12) — the 2-decimal display rounding happens in the UI
 * (FR-031).
 */
public record PositionValuation(
        String ticker,
        String market,
        BigDecimal quantity,
        String nativeCurrency,
        boolean valued,
        Optional<BigDecimal> marketPrice,
        Optional<BigDecimal> nativeMarketValue,
        Optional<BigDecimal> valueInEUR,
        Optional<BigDecimal> valueInUSD,
        Optional<BigDecimal> portfolioWeight,
        String sector,
        Optional<Instant> priceObservedAt) {

    public PositionValuation {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(nativeCurrency, "nativeCurrency");
        Objects.requireNonNull(sector, "sector");
        marketPrice = orEmpty(marketPrice);
        nativeMarketValue = orEmpty(nativeMarketValue);
        valueInEUR = orEmpty(valueInEUR);
        valueInUSD = orEmpty(valueInUSD);
        portfolioWeight = orEmpty(portfolioWeight);
        priceObservedAt = orEmpty(priceObservedAt);
        if (!valued && (marketPrice.isPresent() || nativeMarketValue.isPresent()
                || valueInEUR.isPresent() || valueInUSD.isPresent())) {
            throw new IllegalArgumentException("an unvalued position must carry no monetary value");
        }
    }

    /** An unvalued Position — no market price was available. Monetary fields are all absent (never 0). */
    public static PositionValuation unvalued(String ticker, String market, BigDecimal quantity,
                                             String nativeCurrency, String sector) {
        return new PositionValuation(ticker, market, quantity, nativeCurrency, false,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), sector, Optional.empty());
    }

    private static <T> Optional<T> orEmpty(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }
}
