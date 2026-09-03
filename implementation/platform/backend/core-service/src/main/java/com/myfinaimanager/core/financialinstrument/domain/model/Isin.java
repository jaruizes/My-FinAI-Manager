package com.myfinaimanager.core.financialinstrument.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An ISO 6166 ISIN — two-letter country prefix, nine alphanumerics, one check digit (EN004 §6,
 * FD002 §6). Shape only; the check digit is not verified. Optional reference information — it does
 * <strong>not</strong> replace {@code ticker + market} as the listing identity (EN004 §7).
 */
public record Isin(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");

    public Isin {
        Objects.requireNonNull(value, "isin");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("ISIN must match ISO 6166 shape [A-Z]{2}[A-Z0-9]{9}[0-9]: " + value);
        }
    }

    public static Isin of(String value) {
        return new Isin(value);
    }

    public static boolean hasValidShape(String value) {
        return value != null && SHAPE.matcher(value.strip().toUpperCase(Locale.ROOT)).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
