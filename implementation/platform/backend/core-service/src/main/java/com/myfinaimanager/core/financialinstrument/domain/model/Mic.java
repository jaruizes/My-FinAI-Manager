package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An ISO 10383 Market Identifier Code — exactly four uppercase alphanumerics (EN004 §6; VC-002).
 * Trimmed and upper-cased on construction; the shape is validated but not checked against the ISO
 * registry (a curated {@code markets.csv} is the source of truth).
 */
public record Mic(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[A-Z0-9]{4}$");

    public Mic {
        Objects.requireNonNull(value, "mic");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("MIC must be 4 uppercase alphanumerics (ISO 10383): " + value);
        }
    }

    public static Mic of(String value) {
        return new Mic(value);
    }

    public static boolean hasValidShape(String value) {
        return value != null && SHAPE.matcher(value.strip().toUpperCase(Locale.ROOT)).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
