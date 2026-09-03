package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A normalized trading ticker (EN004 §7). Non-blank, trimmed, upper-cased, at most 20 characters —
 * aligned with the FD001 Position {@code ticker} rules. The Yahoo provider suffix is removed
 * upstream by the mapping-driven normalizer, never here.
 */
public record Ticker(String value) {

    public static final int MAX_LENGTH = 20;

    public Ticker {
        Objects.requireNonNull(value, "ticker");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("ticker must be at most " + MAX_LENGTH + " characters: " + value);
        }
    }

    public static Ticker of(String value) {
        return new Ticker(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
