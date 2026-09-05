package com.myfinaimanager.core.portfolioanalysis.domain.exceptions;

import java.util.UUID;

/**
 * The Portfolio referenced by an analysis operation does not exist (or is not the current
 * investor's). {@code portfolioanalysis}'s own type — translated by {@code
 * PortfolioContextGatewayAdapter} from FD003's {@code portfolio.domain.exceptions
 * .PortfolioNotFoundException} at the ACL boundary (AR-062: another module's exception type never
 * reaches beyond the sole adapter that imports it). {@code PortfolioAnalysisExceptionHandler} maps
 * this to the same {@code 404 /problems/portfolio-not-found} shape FD003/FD004 already use, so the
 * public HTTP contract is identical either way.
 */
public class PortfolioNotFoundException extends RuntimeException {

    private final String portfolioId;

    public PortfolioNotFoundException(UUID portfolioId) {
        super("No portfolio found for id " + portfolioId);
        this.portfolioId = portfolioId.toString();
    }

    /** The requested portfolio id, for the problem {@code instance}. */
    public String portfolioId() {
        return portfolioId;
    }
}
