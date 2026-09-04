package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One Position's inputs to {@link PortfolioValuationCalculator} (FD004). Gathered by
 * {@code PortfolioValuationService} from the persisted Position plus {@code MarketDataGateway}
 * lookups. A missing {@code price} means the Position cannot be valued (FR-017).
 *
 * @param ticker          canonical ticker (EN004)
 * @param market          canonical MIC (EN004)
 * @param quantity        the held quantity, &gt; 0 (FD001)
 * @param nativeCurrency  {@code "EUR"} or {@code "USD"} — the Position currency (FD002)
 * @param price           latest market price in the native currency, if available
 * @param priceObservedAt when the price was observed, if available
 * @param sector          provider classification string, if available (absent ⇒ {@code "Unclassified"})
 */
public record PositionInput(
        String ticker,
        String market,
        BigDecimal quantity,
        String nativeCurrency,
        Optional<BigDecimal> price,
        Optional<Instant> priceObservedAt,
        Optional<String> sector) {

    public PositionInput {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(nativeCurrency, "nativeCurrency");
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be a positive amount");
        }
        price = price == null ? Optional.empty() : price;
        priceObservedAt = priceObservedAt == null ? Optional.empty() : priceObservedAt;
        sector = sector == null ? Optional.empty() : sector;
    }
}
