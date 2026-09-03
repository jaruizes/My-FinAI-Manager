package com.myfinaimanager.core.portfolio.domain.model;

/**
 * Canonical validation-failure tokens for portfolio creation. This enum mirrors the {@code code}
 * enum in {@code implementation/platform/contracts/openapi/openapi.yaml} — the contract is the
 * single source of truth for the wire values, and every constant here MUST appear there.
 *
 * <p>{@code REQUIRED … NAME_TOO_LONG} are FD001 structural/business codes.
 * {@code INSTRUMENT_NOT_IN_CATALOG} (FD002) means a position's {@code ticker + market + currency}
 * does not, as a whole, correspond to one active Financial Instrument listing in the platform
 * catalog (unknown instrument, listing on a different market, inactive listing, or a submitted
 * currency that differs from the listing's).
 */
public enum ValidationCode {
    REQUIRED,
    AT_LEAST_ONE,
    INVALID_NUMBER,
    INVALID_DATE,
    NOT_POSITIVE,
    DUPLICATE_INSTRUMENT,
    FUTURE_DATE,
    CURRENCY_FORMAT,
    NAME_TOO_LONG,
    INSTRUMENT_NOT_IN_CATALOG
}
