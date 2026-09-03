package com.myfinaimanager.core.financialinstrument.domain.model;

/**
 * Raw, normalized-but-not-yet-domain Financial Instrument listing input (all strings) — produced by
 * the Yahoo source adapter after the mapping-driven normalizer has resolved the canonical
 * {@code ticker} / {@code mic} / {@code currency}, and consumed by
 * {@link FinancialInstrumentListing#fromRaw(NewListing)}. {@code isin}, {@code externalReference},
 * {@code instrumentType}, {@code providerSymbol}, {@code active} may be {@code null} / blank.
 */
public record NewListing(
        String name,
        String ticker,
        String mic,
        String currency,
        String isin,
        String externalReference,
        String instrumentType,
        String providerSymbol,
        String active) {
}
