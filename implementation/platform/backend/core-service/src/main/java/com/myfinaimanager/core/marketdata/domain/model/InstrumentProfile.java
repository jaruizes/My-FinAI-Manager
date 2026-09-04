package com.myfinaimanager.core.marketdata.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Provider-neutral company / instrument profile used for enrichment (sector allocation, display).
 * A missing sector is the explicit {@link Sector#UNCLASSIFIED} — never {@code null}, never inferred
 * (FR-035). {@code providerExchange} is the raw provider exchange description kept as
 * <strong>metadata only</strong>; it is never used as, or mapped to, the canonical ISO 10383 MIC
 * (EN004 stays canonical for identity — FR-015).
 *
 * @param ticker           provider-reported ticker
 * @param name             company / instrument name
 * @param sector           classification, or {@link Sector#UNCLASSIFIED}; never {@code null}
 * @param industry         finer classification when available; nullable
 * @param currency         provider currency metadata (EUR/USD), or {@code null}
 * @param providerExchange raw provider exchange string; metadata only; nullable
 * @param source           the data source
 * @param observedAt       retrieval time; nullable (a profile is not time-critical)
 */
public record InstrumentProfile(
        String ticker,
        String name,
        Sector sector,
        String industry,
        SupportedCurrency currency,
        String providerExchange,
        DataSource source,
        Instant observedAt) {

    public InstrumentProfile {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sector, "sector");
        Objects.requireNonNull(source, "source");
    }
}
