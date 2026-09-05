package com.myfinaimanager.core.portfolioanalysis.domain.model;

/**
 * A normalized, Investor-safe reason a {@link PortfolioAnalysis} ended {@code FAILED} — never a raw
 * provider message or stack trace (FD005 §32; contract {@code portfolio-analysis-ports.md} Q3).
 */
public enum FailureReason {
    /** The AI provider is not configured (e.g. no API key) — mirrors EN006's own not-configured case. */
    NOT_CONFIGURED,
    /** The provider was unreachable, rejected the credentials, or rate-limited the call. */
    PROVIDER_UNAVAILABLE,
    /** An input or output guardrail rejected the request/response. */
    GUARDRAIL_REJECTED,
    /** The provider's response did not conform to the required structured shape. */
    INVALID_OUTPUT,
    /** The Portfolio's valuation is not sufficient to analyze (no valued position, or FAILED/PENDING). */
    INSUFFICIENT_DATA,
    /** The provider call exceeded its bounded timeout. */
    TIMEOUT,
    /** Any other failure not meaningfully distinct to the Investor (budget/config/unexpected). */
    UNKNOWN
}
