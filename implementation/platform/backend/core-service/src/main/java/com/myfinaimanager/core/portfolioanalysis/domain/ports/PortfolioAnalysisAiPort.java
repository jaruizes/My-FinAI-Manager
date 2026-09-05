package com.myfinaimanager.core.portfolioanalysis.domain.ports;

import com.myfinaimanager.core.portfolioanalysis.domain.exceptions.AnalysisFailedException;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisContext;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysisResult;

/**
 * Anti-corruption port over the {@code ai} module (EN006; AR-062; contract
 * {@code portfolio-analysis-ports.md} C2). Implemented by the <strong>sole</strong> adapter
 * ({@code PortfolioAnalysisAiAdapter} in {@code portfolioanalysis.infrastructure.ai}) allowed to
 * import {@code com.myfinaimanager.core.ai.*} — it calls {@code ai.business.GenerateAiUseCase
 * .generate(...)} exactly once and translates the result (or any {@code AiException}) into this
 * module's own vocabulary. No {@code ai.*} type ever crosses this port (Q1, Q2).
 */
public interface PortfolioAnalysisAiPort {

    /**
     * @throws AnalysisFailedException every {@code ai.domain.exceptions.AiException} subtype is
     *                                  translated to this, carrying a normalized
     *                                  {@link com.myfinaimanager.core.portfolioanalysis.domain.model.FailureReason}
     *                                  (Q3). Never retried — EN006's own bounded retry already
     *                                  applied (Q4).
     */
    PortfolioAnalysisResult analyze(PortfolioAnalysisContext context, String correlationId);
}
