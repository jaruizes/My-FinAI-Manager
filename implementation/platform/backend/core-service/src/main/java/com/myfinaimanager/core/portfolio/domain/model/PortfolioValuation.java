package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The latest deterministic valuation of one Portfolio (FD004 §12; FR-020). Exactly one snapshot is
 * kept per Portfolio — re-valuation replaces it. No history.
 *
 * <p>Totals are {@link Optional}: a total is present only when it could be produced from the valued
 * Positions (a valued USD Position with no USD&rarr;EUR FX ⇒ no EUR total). Absent, never {@code 0}
 * (FR-018, FR-019). {@code marketDataAsOf} / {@code fxDataAsOf} carry the oldest observation time of
 * the data actually used, so the Investor can judge freshness (§13 BR-013).
 */
public record PortfolioValuation(
        PortfolioId portfolioId,
        ValuationStatus status,
        Instant calculatedAt,
        Optional<BigDecimal> totalValueEUR,
        Optional<BigDecimal> totalValueUSD,
        Optional<Instant> marketDataAsOf,
        Optional<Instant> fxDataAsOf,
        List<PositionValuation> positions,
        List<SectorAllocation> sectors) {

    public PortfolioValuation {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(calculatedAt, "calculatedAt");
        totalValueEUR = orEmpty(totalValueEUR);
        totalValueUSD = orEmpty(totalValueUSD);
        marketDataAsOf = orEmpty(marketDataAsOf);
        fxDataAsOf = orEmpty(fxDataAsOf);
        positions = positions == null ? List.of() : List.copyOf(positions);
        sectors = sectors == null ? List.of() : List.copyOf(sectors);
    }

    private static <T> Optional<T> orEmpty(Optional<T> value) {
        return value == null ? Optional.empty() : value;
    }
}
