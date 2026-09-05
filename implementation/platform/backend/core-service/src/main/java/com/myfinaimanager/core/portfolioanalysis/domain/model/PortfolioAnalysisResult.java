package com.myfinaimanager.core.portfolioanalysis.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * The AI-produced, already schema-validated content of a completed analysis, plus the invocation
 * metadata the persisted row needs (data-model.md §2; AR-062 correction). Produced by {@code
 * PortfolioAnalysisAiPort.analyze(...)} — no {@code ai.*} type appears here (contract
 * {@code portfolio-analysis-ports.md} Q2).
 */
public record PortfolioAnalysisResult(
        DiversificationLevel overallDiversification,
        String explanation,
        List<PortfolioAnalysis.Insight> insights,
        List<PortfolioAnalysis.Risk> risks,
        String provider,
        String model,
        String promptId,
        String promptVersion,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        BigDecimal estimatedCost) {

    public PortfolioAnalysisResult {
        Objects.requireNonNull(overallDiversification, "overallDiversification");
        Objects.requireNonNull(explanation, "explanation");
        if (explanation.isBlank()) {
            throw new IllegalArgumentException("explanation must not be blank");
        }
        Objects.requireNonNull(insights, "insights");
        insights = List.copyOf(insights);
        Objects.requireNonNull(risks, "risks");
        risks = List.copyOf(risks);
        requireNonBlank(provider, "provider");
        requireNonBlank(model, "model");
        requireNonBlank(promptId, "promptId");
        requireNonBlank(promptVersion, "promptVersion");
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        if (estimatedCost == null || estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("estimatedCost must be non-negative");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
