package com.myfinaimanager.core.portfolio.domain.model;

/**
 * The explicit state of a Portfolio valuation (FD004 §6; FR-015). Exactly one of these values is
 * always present on a snapshot.
 *
 * <ul>
 *   <li>{@link #PENDING} — no valuation result has been produced yet (the brief window before the
 *       synchronous post-creation valuation writes its result, or a portfolio with no snapshot row).
 *       Never produced by {@code PortfolioValuationCalculator}.</li>
 *   <li>{@link #COMPLETED} — every Position was valued (price + the necessary FX); a missing sector
 *       alone does not prevent this (FR-016).</li>
 *   <li>{@link #PARTIAL} — some Position could not be valued, or a needed FX rate was missing, or
 *       only one of the EUR / USD totals could be produced (FR-017, FR-018).</li>
 *   <li>{@link #FAILED} — no Position could be valued at all, or neither total could be produced
 *       (FR-018).</li>
 * </ul>
 */
public enum ValuationStatus {
    PENDING,
    COMPLETED,
    PARTIAL,
    FAILED
}
