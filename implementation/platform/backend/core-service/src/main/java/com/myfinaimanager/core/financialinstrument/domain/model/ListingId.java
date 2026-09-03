package com.myfinaimanager.core.financialinstrument.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier for a Financial Instrument listing. Derived <strong>deterministically</strong>
 * from {@code ticker + MIC} (research.md D13) so the same listing always gets the same id on every
 * machine and run — the committed fixtures stay human-editable (no id column), fixture assertions
 * are reproducible, and the id survives a database wipe + re-import.
 *
 * <p>Uses {@link UUID#nameUUIDFromBytes(byte[])} (RFC 4122 name-based, MD5 / version 3) over a
 * fixed namespace prefix — the JDK-native deterministic UUID. "Deterministic" is the requirement;
 * v3 vs v5 is immaterial here and avoids a hand-rolled SHA-1 implementation.
 */
public record ListingId(UUID value) {

    private static final String NAMESPACE = "finai:financialinstrument:listing:";

    public ListingId {
        Objects.requireNonNull(value, "listing id");
    }

    public static ListingId of(UUID value) {
        return new ListingId(value);
    }

    public static ListingId deterministic(Ticker ticker, Mic market) {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(market, "market");
        String name = NAMESPACE + ticker.value() + "|" + market.value();
        return new ListingId(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
