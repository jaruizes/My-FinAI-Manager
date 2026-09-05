package com.myfinaimanager.core.portfolioanalysis.business;

import java.util.UUID;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;

/** Inbound port: request a new analysis for a Portfolio (US1, US4). */
public interface RequestPortfolioAnalysisUseCase {

    /**
     * Requests an analysis triggered automatically right after Portfolio creation (US1). No
     * duplicate-check is performed — a brand-new Portfolio can have no prior analysis (research D5).
     */
    PortfolioAnalysis requestAutomatic(UUID portfolioId);

    /**
     * Requests an analysis triggered by the Investor ("Run analysis again" — US4).
     *
     * @throws AnalysisAlreadyInProgressException the latest analysis for this Portfolio is still
     *                                             {@code PENDING}/{@code RUNNING} (FR-013)
     * @throws com.myfinaimanager.core.portfolioanalysis.domain.exceptions.PortfolioNotFoundException
     *         the portfolio does not exist (or is not the current investor's)
     */
    PortfolioAnalysis requestManual(UUID portfolioId);
}
