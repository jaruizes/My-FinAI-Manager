package com.myfinaimanager.core.portfolioanalysis.domain.ports;

import java.util.Optional;
import java.util.UUID;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisAlreadyInProgressException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisId;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;

/**
 * Outbound persistence port for {@link PortfolioAnalysis} (data-model.md §3; contract
 * {@code portfolio-analysis-ports.md} C3).
 */
public interface PortfolioAnalysisRepository {

    /**
     * An insert for a new {@link AnalysisId}, an update (status transition) for one already
     * persisted — never touches a row with a different id (R1).
     *
     * @throws AnalysisAlreadyInProgressException a new analysis's Portfolio already has an open
     *                                             ({@code PENDING}/{@code RUNNING}) request — the
     *                                             database's unique index is authoritative (R2)
     */
    PortfolioAnalysis save(PortfolioAnalysis analysis);

    /** The latest (greatest {@code requestedAt}) analysis for this Portfolio, if any (R3). */
    Optional<PortfolioAnalysis> findLatestByPortfolioId(UUID portfolioId);

    Optional<PortfolioAnalysis> findById(AnalysisId id);
}
