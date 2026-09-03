package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Objects;

/**
 * Identifies a financial instrument <em>within a portfolio</em> by {@code ticker + market}
 * (BR-003, FR-012, §14.1). Value equality drives duplicate-position detection (BR-004). Two
 * positions with the same ticker but different markets are distinct (FR-014).
 *
 * <p>FD001 does not create or enrich a canonical Financial Instrument record — this only holds the
 * identifying values the investor supplies.
 */
public record InstrumentRef(Ticker ticker, Market market) {

    public InstrumentRef {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(market, "market");
    }

    @Override public String toString() {
        return ticker.value() + "@" + market.value();
    }
}
