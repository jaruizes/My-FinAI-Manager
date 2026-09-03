package com.myfinaimanager.core.portfolio.domain.model;

/**
 * Raw, unvalidated position input handed to {@link Portfolio#create}. All fields are the exact
 * strings the investor supplied (or {@code null} for an omitted optional). Parsing and validation
 * happen inside the domain, not the web layer (research.md D2), so that a single {@code create}
 * call can collect every {@link Violation}.
 *
 * @param ticker               required
 * @param market               required
 * @param quantity             required, decimal string
 * @param currency             required, ISO 4217 code
 * @param initialPurchaseDate  optional; ISO 8601 date string, or {@code null}
 * @param averagePurchasePrice optional; decimal string, or {@code null} (never {@code "0"})
 */
public record NewPosition(
        String ticker,
        String market,
        String quantity,
        String currency,
        String initialPurchaseDate,
        String averagePurchasePrice) {
}
