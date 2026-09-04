package com.myfinaimanager.core.portfolio.domain.ports;

import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import java.util.Optional;

/**
 * Outbound port for the <em>latest</em> Portfolio valuation snapshot (FD004 §12; FR-020, FR-021).
 * Exactly one snapshot is kept per Portfolio — {@link #upsertLatest} replaces it. No history.
 * Implemented by a persistence adapter that owns only the FD004 valuation tables; it never writes
 * the {@code portfolio} / {@code position} tables (SC-008).
 */
public interface PortfolioValuationRepository {

    /**
     * Replace the single stored snapshot for {@code valuation.portfolioId()} with {@code valuation},
     * atomically (delete-then-insert in one transaction). Idempotent: re-running valuation for a
     * Portfolio never accumulates duplicate Position/sector rows (FR-021, SC-002).
     */
    void upsertLatest(PortfolioValuation valuation);

    /** The latest snapshot for this Portfolio, if one has been persisted. */
    Optional<PortfolioValuation> findByPortfolioId(PortfolioId portfolioId);
}
