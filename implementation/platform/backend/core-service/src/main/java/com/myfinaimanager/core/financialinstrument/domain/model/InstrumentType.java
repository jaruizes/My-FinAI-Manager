package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Locale;

/**
 * Descriptive classification of a listing — best-effort from the source (spec FR-040). The initial
 * selectable catalog is equities and ETFs; anything else the source yields is {@code OTHER}. This
 * is metadata only: FD002 search does <strong>not</strong> filter by type.
 */
public enum InstrumentType {
    EQUITY,
    ETF,
    OTHER;

    /**
     * Best-effort mapping from a free-text source category (e.g. the Yahoo "Category Name" column).
     * Never throws — an unrecognised or blank value is {@link #OTHER}.
     */
    public static InstrumentType fromSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String s = raw.strip().toLowerCase(Locale.ROOT);
        if (s.contains("etf") || s.contains("exchange traded") || s.contains("exchange-traded")) {
            return ETF;
        }
        if (s.contains("equity") || s.contains("stock") || s.contains("share") || s.contains("common")) {
            return EQUITY;
        }
        return OTHER;
    }
}
