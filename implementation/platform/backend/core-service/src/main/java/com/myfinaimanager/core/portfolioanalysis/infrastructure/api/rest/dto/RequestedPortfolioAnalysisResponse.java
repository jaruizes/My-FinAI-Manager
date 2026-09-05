package com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto;

/**
 * Response body for {@code POST /api/portfolios/{portfolioId}/analysis} (202) — mirrors the
 * {@code RequestedPortfolioAnalysis} schema (contracts/openapi-fragment.md). {@code status} is
 * always {@code "PENDING"} — the accepted analysis has not started processing yet.
 */
public record RequestedPortfolioAnalysisResponse(String analysisId, String status, String requestedAt) {
}
