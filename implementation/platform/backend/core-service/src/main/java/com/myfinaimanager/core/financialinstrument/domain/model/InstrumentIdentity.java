package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Objects;

/**
 * The identity of a Financial Instrument listing within the catalog: {@code ticker + market} —
 * exactly the FD001 Position identity rule (EN004 §7; FR-006). Value equality; the database
 * enforces {@code UNIQUE (ticker, market_mic)}.
 */
public record InstrumentIdentity(Ticker ticker, Mic market) {

    public InstrumentIdentity {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(market, "market");
    }

    @Override
    public String toString() {
        return ticker.value() + "@" + market.value();
    }
}
