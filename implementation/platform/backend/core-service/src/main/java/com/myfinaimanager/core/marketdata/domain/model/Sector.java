package com.myfinaimanager.core.marketdata.domain.model;

import java.util.Objects;

/**
 * A company's sector / industry classification as supplied by the data provider, or the explicit
 * {@link #UNCLASSIFIED} state when the provider gave none. EN005 never infers a sector — a missing
 * classification stays explicitly unknown (enabler §17; FR-035).
 */
public final class Sector {

    /** No classification available from the provider. */
    public static final Sector UNCLASSIFIED = new Sector(null);

    private final String classification;

    private Sector(String classification) {
        this.classification = classification;
    }

    /** {@link #UNCLASSIFIED} for a null/blank value; otherwise a classified sector with the trimmed value. */
    public static Sector of(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return UNCLASSIFIED;
        }
        return new Sector(raw.trim());
    }

    public boolean isClassified() {
        return classification != null;
    }

    /** The classification string, or {@code null} when {@link #isClassified()} is false. */
    public String classification() {
        return classification;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Sector other && Objects.equals(classification, other.classification);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(classification);
    }

    @Override
    public String toString() {
        return classification == null ? "UNCLASSIFIED" : classification;
    }
}
