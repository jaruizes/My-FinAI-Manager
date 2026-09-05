package com.myfinaimanager.core.portfolioanalysis.domain.model;

/** How a {@link PortfolioAnalysis} request was created (FD005 US1/US4). */
public enum CreationTrigger {
    /** Requested automatically after Portfolio creation (US1). */
    AUTOMATIC,
    /** Requested by the Investor via "Run analysis again" (US4). */
    MANUAL
}
