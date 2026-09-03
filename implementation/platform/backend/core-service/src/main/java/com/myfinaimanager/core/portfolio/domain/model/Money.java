package com.myfinaimanager.core.portfolio.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A monetary amount in a specific {@link Currency}. Used by FD001 for a position's average
 * purchase price (BR-007, FR-020, FR-025). Exact decimal (DR-011); the amount is strictly greater
 * than zero — an acquisition price cannot be zero or negative (spec A3).
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("money amount must be greater than zero");
        }
    }

    /** Parse a decimal string exactly (no rounding), in the given currency. */
    public static Money parse(String decimal, Currency currency) {
        return new Money(new BigDecimal(decimal.strip()), currency);
    }

    @Override public String toString() {
        return amount.toPlainString() + " " + currency.code();
    }
}
