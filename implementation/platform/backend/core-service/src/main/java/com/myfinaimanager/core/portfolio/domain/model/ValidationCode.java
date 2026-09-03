package com.myfinaimanager.core.portfolio.domain.model;

/**
 * Canonical validation-failure tokens for FD001. This enum mirrors the {@code code} enum in
 * {@code implementation/platform/contracts/openapi/openapi.yaml} — the contract is the single
 * source of truth for the wire values, and every constant here MUST appear there.
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
    NAME_TOO_LONG
}
