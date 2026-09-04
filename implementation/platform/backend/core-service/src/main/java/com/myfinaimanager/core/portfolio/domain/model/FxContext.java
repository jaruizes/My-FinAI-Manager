package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * The FX rates available to {@link PortfolioValuationCalculator} for one valuation run (FD004 §8).
 * Only the directions the Portfolio actually needs are gathered (research D8); a needed-but-absent
 * rate makes the affected cross-currency value / total absent and the valuation {@code PARTIAL}
 * (FR-018).
 *
 * @param usdToEur           rate to convert a USD amount to EUR, if available
 * @param usdToEurObservedAt when {@code usdToEur} was observed, if available
 * @param eurToUsd           rate to convert a EUR amount to USD, if available
 * @param eurToUsdObservedAt when {@code eurToUsd} was observed, if available
 */
public record FxContext(
        Optional<BigDecimal> usdToEur,
        Optional<Instant> usdToEurObservedAt,
        Optional<BigDecimal> eurToUsd,
        Optional<Instant> eurToUsdObservedAt) {

    public FxContext {
        usdToEur = usdToEur == null ? Optional.empty() : usdToEur;
        usdToEurObservedAt = usdToEurObservedAt == null ? Optional.empty() : usdToEurObservedAt;
        eurToUsd = eurToUsd == null ? Optional.empty() : eurToUsd;
        eurToUsdObservedAt = eurToUsdObservedAt == null ? Optional.empty() : eurToUsdObservedAt;
    }

    /** No FX rates available. */
    public static FxContext none() {
        return new FxContext(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
