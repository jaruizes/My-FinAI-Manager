package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Locale;
import java.util.Objects;

/**
 * An instrument ticker symbol (FD001 §5, BR-003). Non-blank, trimmed, upper-cased. Not assumed to
 * be globally unique on its own — see {@link InstrumentRef}.
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
            throw new IllegalArgumentException("ticker must be at most " + MAX_LENGTH + " characters");
        }
    }

    @Override public String toString() {
        return value;
    }
}
