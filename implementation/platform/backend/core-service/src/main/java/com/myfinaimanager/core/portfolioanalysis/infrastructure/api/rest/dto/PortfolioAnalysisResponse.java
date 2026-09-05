package com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto;

import java.util.List;

/**
 * Response body for {@code GET /api/portfolios/{portfolioId}/analysis/latest} — mirrors the
 * {@code PortfolioAnalysis} schema in the OpenAPI contract (contracts/openapi-fragment.md).
 * {@code status=NONE} when no analysis has ever been requested; content fields ({@code
 * overallDiversification}/{@code keyInsights}/{@code risks}) are present only when {@code status
 * =COMPLETED}. No {@code provider}/{@code model}/{@code promptId}/{@code promptVersion}/token/cost
 * field ever appears here (FD005 §28) — that metadata stays in the persisted row and EN006
 * telemetry only. Built by {@code PortfolioAnalysisResponseMapper}.
 */
public record PortfolioAnalysisResponse(
        String status,
        String requestedAt,
        String completedAt,
        OverallDiversificationResponse overallDiversification,
        List<KeyInsightResponse> keyInsights,
        List<RiskResponse> risks) {

    public record OverallDiversificationResponse(String level, String explanation) {
    }

    public record KeyInsightResponse(String type, String message) {
    }

    public record RiskResponse(String type, String severity, String title, String explanation) {
    }
}
