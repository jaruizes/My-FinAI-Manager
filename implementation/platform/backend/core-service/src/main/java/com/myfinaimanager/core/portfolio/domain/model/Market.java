package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The market / trading venue a position is identified on (BR-003, FR-015). Non-blank, trimmed,
 * upper-cased. An ISO 10383 Market Identifier Code (MIC) shape is recognised but not required —
 * whatever the investor supplies is recorded verbatim (spec A5). No external reference-data lookup.
 */
public record Market(String value) {

    public static final int MAX_LENGTH = 20;
    private static final Pattern MIC_SHAPE = Pattern.compile("^[A-Z0-9]{4}$");

    public Market {
        Objects.requireNonNull(value, "market");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("market must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("market must be at most " + MAX_LENGTH + " characters");
        }
    }

    /** True when the value looks like an ISO 10383 MIC (4 alphanumerics). Informational only. */
    public boolean isMicShaped() {
        return MIC_SHAPE.matcher(value).matches();
    }

    @Override public String toString() {
        return value;
    }
}
