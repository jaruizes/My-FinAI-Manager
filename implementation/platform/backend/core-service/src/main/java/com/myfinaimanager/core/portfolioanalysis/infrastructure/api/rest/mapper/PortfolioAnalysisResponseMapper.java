package com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.myfinaimanager.core.portfolioanalysis.domain.model.AnalysisStatus;
import com.myfinaimanager.core.portfolioanalysis.domain.model.PortfolioAnalysis;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto.PortfolioAnalysisResponse;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto.PortfolioAnalysisResponse.KeyInsightResponse;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto.PortfolioAnalysisResponse.OverallDiversificationResponse;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto.PortfolioAnalysisResponse.RiskResponse;
import com.myfinaimanager.core.portfolioanalysis.infrastructure.api.rest.dto.RequestedPortfolioAnalysisResponse;

/**
 * Maps the domain {@link PortfolioAnalysis} to the REST {@link PortfolioAnalysisResponse}. Content
 * fields are populated only for {@code COMPLETED} — never leaked for any other status, and never
 * including {@code provider}/{@code model}/{@code promptId}/token/cost (FD005 §28). This formatting
 * is the OpenAPI contract; the contract test guards it.
 */
@Component
public class PortfolioAnalysisResponseMapper {

    /** {@code Optional.empty()} maps to the explicit {@code NONE} placeholder. */
    public PortfolioAnalysisResponse toResponse(Optional<PortfolioAnalysis> analysis) {
        if (analysis.isEmpty()) {
            return new PortfolioAnalysisResponse("NONE", null, null, null, null, null);
        }
        PortfolioAnalysis a = analysis.get();
        boolean completed = a.status() == AnalysisStatus.COMPLETED;
        return new PortfolioAnalysisResponse(
                a.status().name(),
                a.requestedAt().toString(),
                a.completedAt().map(Object::toString).orElse(null),
                completed ? toOverall(a) : null,
                completed ? toInsights(a) : null,
                completed ? toRisks(a) : null);
    }

    public RequestedPortfolioAnalysisResponse toRequested(PortfolioAnalysis a) {
        return new RequestedPortfolioAnalysisResponse(
                a.id().toString(), a.status().name(), a.requestedAt().toString());
    }

    private static OverallDiversificationResponse toOverall(PortfolioAnalysis a) {
        return new OverallDiversificationResponse(
                a.overallDiversification().map(Enum::name).orElse(null), a.summary().orElse(null));
    }

    private static List<KeyInsightResponse> toInsights(PortfolioAnalysis a) {
        return a.insights().stream()
                .sorted(Comparator.comparingInt(PortfolioAnalysis.Insight::order))
                .map(i -> new KeyInsightResponse(i.type(), i.message()))
                .toList();
    }

    private static List<RiskResponse> toRisks(PortfolioAnalysis a) {
        return a.risks().stream()
                .sorted(Comparator.comparingInt(PortfolioAnalysis.Risk::order))
                .map(r -> new RiskResponse(r.type().name(), r.severity().name(), r.title(), r.explanation()))
                .toList();
    }
}
