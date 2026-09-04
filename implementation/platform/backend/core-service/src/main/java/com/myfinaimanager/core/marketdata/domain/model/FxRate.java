package com.myfinaimanager.core.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A currency-conversion rate {@code from -> to}, provider-neutral. {@code rate} is decimal-safe
 * ({@link BigDecimal}) and always positive — a missing provider rate is surfaced as
 * {@code FxRateUnavailableException}, never a default of {@code 1} or {@code 0} (FR-008, FR-012).
 * EN005 supplies the rate only; the arithmetic {@code convertedValue = value * rate} is
 * deterministic business logic outside EN005 (enabler §15).
 *
 * @param from             source currency (!= {@code to})
 * @param to               target currency
 * @param rate             &gt; 0
 * @param observedAt       the rate's observation time — the provider's publication date when it
 *                         carries one (Frankfurter dates its ECB rates), else the retrieval time
 * @param observedAtSource which of the two {@code observedAt} represents
 * @param source           the data source
 */
public record FxRate(
        SupportedCurrency from,
        SupportedCurrency to,
        BigDecimal rate,
        Instant observedAt,
        ObservedAtSource observedAtSource,
        DataSource source) {

    public FxRate {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(observedAtSource, "observedAtSource");
        Objects.requireNonNull(source, "source");
        if (from == to) {
            throw new IllegalArgumentException("from and to must differ");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be a positive amount");
        }
    }
}
