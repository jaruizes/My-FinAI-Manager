package com.myfinaimanager.core.portfolio.domain.model;

import java.util.Objects;

/**
 * One field-level validation problem. {@code field} is a JSON-pointer-ish path
 * ({@code name}, {@code positions}, {@code positions[1].quantity}); {@code code} is one of
 * {@link ValidationCode}; {@code message} is human-readable and non-technical (FR-024).
 */
public record Violation(String field, String code, String message) {

    public Violation {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public static Violation of(String field, ValidationCode code, String message) {
        return new Violation(field, code.name(), message);
    }
}
