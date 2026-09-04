package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;

/** Inbound port: (re)compute and persist the latest valuation snapshot for one Portfolio (FD004). */
public interface ValuePortfolioUseCase {

    /**
     * Value the Portfolio and replace its latest snapshot. Reads market data through
     * {@code MarketDataGateway}; never writes the {@code portfolio} / {@code position} tables. A
     * missing price / FX / sector is handled inside the deterministic calculation (status
     * {@code PARTIAL} / {@code FAILED}), not by throwing.
     *
     * @throws PortfolioNotFoundException if the id is not the current investor's Portfolio
     */
    void value(PortfolioId portfolioId);
}
