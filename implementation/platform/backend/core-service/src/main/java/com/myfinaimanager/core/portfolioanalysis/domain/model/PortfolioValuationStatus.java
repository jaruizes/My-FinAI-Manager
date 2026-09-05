package com.myfinaimanager.core.portfolioanalysis.domain.model;

/**
 * {@code portfolioanalysis}'s own vocabulary for a Portfolio's valuation state (data-model.md §2) —
 * mirrors FD004's {@code portfolio.domain.model.ValuationStatus} in meaning, but is a distinct type
 * so no FD004 type ever crosses the {@code PortfolioContextGateway} ACL (contract
 * {@code portfolio-analysis-ports.md} P2). {@link #ABSENT} has no FD004 counterpart — it is this
 * module's own name for "no valuation snapshot has ever been persisted for this Portfolio"
 * ({@code PortfolioValuationQueryUseCase.findLatest} returning empty).
 */
public enum PortfolioValuationStatus {
    PENDING,
    COMPLETED,
    PARTIAL,
    FAILED,
    ABSENT
}
