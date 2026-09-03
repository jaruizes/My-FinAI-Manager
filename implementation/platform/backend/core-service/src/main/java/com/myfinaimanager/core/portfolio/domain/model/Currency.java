package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An ISO 4217 currency code (BR-006, FR-016). FD001 validates the <em>shape</em> only — exactly
 * three uppercase ASCII letters — and does not check the code against a currency registry
 * (spec A5). Currency is always entered explicitly; it is never inferred (FR-017).
 */
public record Currency(String code) {

    private static final Pattern ISO_4217_SHAPE = Pattern.compile("^[A-Z]{3}$");

    public Currency {
        Objects.requireNonNull(code, "currency");
        if (!ISO_4217_SHAPE.matcher(code).matches()) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO 4217 code: " + code);
        }
    }

    public static boolean hasValidShape(String code) {
        return code != null && ISO_4217_SHAPE.matcher(code).matches();
    }

    @Override public String toString() {
        return code;
    }
}
