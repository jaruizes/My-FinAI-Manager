package com.myfinaimanager.core.portfolioanalysis.domain.ports;

import java.util.UUID;

import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioContextSnapshot;

/**
 * Anti-corruption port over the {@code portfolio} module (AR-062; contract
 * {@code portfolio-analysis-ports.md} C1). Implemented by the <strong>sole</strong> adapter
 * ({@code PortfolioContextGatewayAdapter} in {@code portfolioanalysis.infrastructure.portfolio})
 * allowed to import {@code com.myfinaimanager.core.portfolio.*} — it calls
 * {@code portfolio.business.PortfolioQueryUseCase.view(...)} then
 * {@code PortfolioValuationQueryUseCase.findLatest(...)} and translates both into this module's own
 * {@link PortfolioContextSnapshot} vocabulary. {@code portfolioId} is a plain {@link UUID} so no
 * {@code portfolio} type reaches {@code portfolioanalysis.domain} (P1, P2).
 */
public interface PortfolioContextGateway {

    /**
     * @throws com.myfinaimanager.core.portfolioanalysis.domain.exceptions.PortfolioNotFoundException
     *         the portfolio does not exist (or is not the current investor's) — translated at this
     *         ACL boundary from FD003's own exception type (P3, AR-062); mapped to the same 404
     *         FD003/FD004 already use
     */
    PortfolioContextSnapshot fetch(UUID portfolioId);
}
