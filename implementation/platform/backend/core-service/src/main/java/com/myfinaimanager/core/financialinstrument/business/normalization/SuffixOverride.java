package com.myfinaimanager.core.financialinstrument.business.normalization;

/**
 * One row of {@code yahoo-exchange-suffix-overrides.csv} — for an {@code (Exchange, yahooSuffix)}
 * pair, the canonical MIC/currency to use, overriding the exchange rule (contracts/reference-mapping.md
 * §2). {@code currency} may be blank (e.g. {@code (ENX, NX)}), which forces the row to be quarantined
 * as {@code AMBIGUOUS_EXCHANGE}.
 */
public record SuffixOverride(String canonicalMic, String currency) {

    public boolean hasCurrency() {
        return currency != null && !currency.isBlank();
    }
}
