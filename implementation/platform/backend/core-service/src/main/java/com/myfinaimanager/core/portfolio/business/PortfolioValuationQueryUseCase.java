package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioValuation;
import java.util.Optional;

/** Inbound port: read the latest valuation snapshot for the current investor's Portfolio (FD004). */
public interface PortfolioValuationQueryUseCase {

    /**
     * The latest snapshot for this Portfolio, or {@link Optional#empty()} when no valuation has
     * been persisted yet (the caller renders an explicit {@code PENDING} result — FR-025).
     * Read-only.
     *
     * @throws PortfolioNotFoundException if the id is not the current investor's Portfolio
     */
    Optional<PortfolioValuation> findLatest(PortfolioId portfolioId);
}
