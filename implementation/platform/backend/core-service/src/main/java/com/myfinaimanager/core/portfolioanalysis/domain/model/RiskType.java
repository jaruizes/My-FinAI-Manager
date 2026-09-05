package com.myfinaimanager.core.portfolioanalysis.domain.model;

/** The closed vocabulary of risk classifications a {@link PortfolioAnalysis.Risk} may carry (FR-031). */
public enum RiskType {
    SECTOR_CONCENTRATION,
    POSITION_CONCENTRATION,
    CURRENCY_CONCENTRATION,
    LOW_DIVERSIFICATION,
    MISSING_SECTOR_EXPOSURE,
    OTHER
}
