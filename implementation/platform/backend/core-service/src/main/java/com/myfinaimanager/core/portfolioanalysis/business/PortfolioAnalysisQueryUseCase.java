package com.myfinaimanager.core.portfolioanalysis.business;

import java.util.Optional;
import java.util.UUID;

import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;

/** Inbound port: read the latest analysis for a Portfolio, regardless of its status (US2). */
public interface PortfolioAnalysisQueryUseCase {

    /**
     * @return the latest (greatest {@code requestedAt}) analysis for this Portfolio, in
     *         <strong>any</strong> status — {@link Optional#empty()} only when no analysis has
     *         ever been requested for it (the caller renders the explicit {@code NONE} state).
     * @throws com.myfinaimanager.core.portfolioanalysis.domain.exceptions.PortfolioNotFoundException
     *         the portfolio does not exist (or is not the current investor's)
     */
    Optional<PortfolioAnalysis> findLatest(UUID portfolioId);
}
