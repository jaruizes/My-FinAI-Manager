package com.myfinaimanager.core.portfolio.business;

import com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioValidationException;

/** Inbound port: create an investment portfolio with its initial positions (FD001). */
public interface CreatePortfolioUseCase {

    /**
     * @throws PortfolioValidationException if the portfolio or a position breaks a business rule
     *                                      (nothing is persisted)
     * @throws com.myfinaimanager.core.portfolio.domain.exceptions.PortfolioNotSavedException
     *                                      if a valid portfolio could not be persisted (transient)
     */
    CreatePortfolioResult create(CreatePortfolioCommand command);
}
