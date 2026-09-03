package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The number of securities held in a position (BR-005, FR-009, FR-025). Exact decimal — never a
 * binary floating-point type (DR-011). Strictly greater than zero. Fractional quantities are
 * allowed (spec A6). The input scale is preserved.
 */
public record Quantity(BigDecimal value) {

    public Quantity {
        Objects.requireNonNull(value, "quantity");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

    /** Parse a decimal string exactly (no rounding). Throws {@link NumberFormatException} if not a number. */
    public static Quantity parse(String decimal) {
        return new Quantity(new BigDecimal(decimal.strip()));
    }

    @Override public String toString() {
        return value.toPlainString();
    }
}
