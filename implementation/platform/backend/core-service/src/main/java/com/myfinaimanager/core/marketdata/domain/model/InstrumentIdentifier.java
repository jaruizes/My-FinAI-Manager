package com.myfinaimanager.core.marketdata.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Provider-neutral identity of a selectable financial-instrument listing: canonical {@code ticker} +
 * ISO 10383 {@code market} (MIC) + {@code currency}, aligned with the platform's Financial Instrument
 * listing / Position identity (EN004 / FD002). {@code ticker} and {@code market} are uppercased and
 * trimmed. This is <strong>not</strong> a Finnhub symbol — the {@code FinnhubSymbolResolver} converts
 * {@code ticker + market} to the provider symbol inside the adapter; {@code currency} is carried on
 * the result (Finnhub {@code /quote} does not return one).
 */
public record InstrumentIdentifier(String ticker, String market, SupportedCurrency currency) {

    public InstrumentIdentifier {
        ticker = normalize(ticker, "ticker");
        market = normalize(market, "market");
        Objects.requireNonNull(currency, "currency");
    }

    private static String normalize(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
