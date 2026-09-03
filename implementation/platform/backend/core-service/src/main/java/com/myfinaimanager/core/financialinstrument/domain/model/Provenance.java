package com.myfinaimanager.core.financialinstrument.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal operational traceability for a reference row (EN004 §21, §33.10; research.md D8). Stored
 * on the {@code market} / {@code financial_instrument} rows themselves — no separate history table
 * in the first version. Never exposed through the public API.
 *
 * @param source           stable token, e.g. {@code YAHOO_CSV} or {@code ISO10383_CSV}
 * @param sourceReference   raw source key (e.g. the raw Yahoo symbol) — nullable
 * @param lastImportedAt   timestamp of the run that last wrote this row — nullable
 */
public record Provenance(String source, String sourceReference, Instant lastImportedAt) {

    public Provenance {
        Objects.requireNonNull(source, "provenance source");
        if (source.isBlank()) {
            throw new IllegalArgumentException("provenance source must not be blank");
        }
    }

    public static Provenance of(String source) {
        return new Provenance(source, null, null);
    }

    public static Provenance of(String source, String sourceReference, Instant lastImportedAt) {
        return new Provenance(source, sourceReference, lastImportedAt);
    }

    public Optional<String> sourceReferenceValue() {
        return Optional.ofNullable(sourceReference);
    }

    public Optional<Instant> lastImportedAtValue() {
        return Optional.ofNullable(lastImportedAt);
    }
}
