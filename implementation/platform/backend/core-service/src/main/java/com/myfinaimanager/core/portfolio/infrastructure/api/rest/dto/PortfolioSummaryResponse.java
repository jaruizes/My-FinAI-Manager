package com.myfinaimanager.core.portfolio.infrastructure.api.rest.dto;

/**
 * One item of {@code GET /api/portfolios} — mirrors the {@code PortfolioSummary} schema in the
 * OpenAPI contract: portfolio identity, name, and position count only. No status, createdAt,
 * investor, or persistence field (FR-021). Built by {@code PortfolioSummaryMapper}.
 */
public record PortfolioSummaryResponse(String id, String name, int positionCount) {
}
