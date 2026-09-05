package com.myfinaimanager.core.portfolioanalysis.domain.model;

/**
 * Lifecycle status of one {@link PortfolioAnalysis} request (FD005 data-model.md §5). Exactly one
 * request is ever {@code PENDING} or {@code RUNNING} at a time per Portfolio (FR-013, DB-enforced).
 */
public enum AnalysisStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
