package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Objects;

/**
 * A portfolio's name (BR-001, spec A7). Non-blank after trimming, at most 120 characters, stored
 * exactly as entered minus surrounding whitespace. Not required to be unique (FR-004).
 */
public record PortfolioName(String value) {

    public static final int MAX_LENGTH = 120;

    public PortfolioName {
        Objects.requireNonNull(value, "portfolio name");
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("portfolio name must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("portfolio name must be at most " + MAX_LENGTH + " characters");
        }
    }

    @Override public String toString() {
        return value;
    }
}
