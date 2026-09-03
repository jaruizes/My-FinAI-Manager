package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotFoundException;
import com.myfinaimanager.core.portfolio.domain.model.Portfolio;
import com.myfinaimanager.core.portfolio.domain.model.PortfolioId;
import java.util.List;

/** Inbound port: read the current investor's portfolios (FD003). Read-only — never persists. */
public interface PortfolioQueryUseCase {

    /** The current investor's portfolios, most-recently-created first (possibly empty). */
    List<Portfolio> list();

    /**
     * The current investor's portfolio with this id.
     *
     * @throws PortfolioNotFoundException if no such portfolio belongs to the current investor
     */
    Portfolio view(PortfolioId id);
}
